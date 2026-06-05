package com.superchat.moderation.service;

import com.superchat.moderation.domain.FilterAction;
import com.superchat.moderation.domain.ModerationIncident;
import com.superchat.moderation.domain.WordList;
import com.superchat.moderation.repo.ModerationIncidentRepository;
import com.superchat.moderation.repo.WordListRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ModerationService {

    private final WordListRepository wordListRepository;
    private final ModerationIncidentRepository incidentRepository;
    private final MeterRegistry meterRegistry;

    public ModerationService(WordListRepository wordListRepository,
                              ModerationIncidentRepository incidentRepository,
                              MeterRegistry meterRegistry) {
        this.wordListRepository = wordListRepository;
        this.incidentRepository = incidentRepository;
        this.meterRegistry = meterRegistry;
    }

    // --- Filter engine ---

    public CheckResult check(UUID orgId, String userId, Long conversationId, String content) {
        if (orgId == null || content == null || content.isBlank()) return CheckResult.pass(content);

        List<WordList> rules = getCachedRules(orgId);
        String workingContent = content;
        String warnedPattern = null;

        for (WordList rule : rules) {
            Pattern pattern = compile(rule);
            if (!pattern.matcher(workingContent).find()) continue;

            recordIncident(orgId, userId, conversationId, rule.getPattern(), rule.getAction());

            switch (rule.getAction()) {
                case BLOCK -> { return CheckResult.block(rule.getPattern()); }
                case REPLACE -> {
                    String repl = rule.getReplacement() != null ? rule.getReplacement() : "***";
                    workingContent = pattern.matcher(workingContent).replaceAll(repl);
                }
                case WARN -> { warnedPattern = rule.getPattern(); /* content passes, but flag it */ }
            }
        }
        // A WARN match lets the message through but signals the sender; PASS is silent.
        return warnedPattern != null
                ? CheckResult.warn(workingContent, warnedPattern)
                : CheckResult.pass(workingContent);
    }

    // --- Word list management ---

    @Transactional
    @CacheEvict(value = "moderationRules", key = "#orgId")
    public WordList addRule(UUID orgId, String pattern, boolean isRegex,
                            String severity, String action, String replacement) {
        WordList rule = new WordList();
        rule.setOrgId(orgId);
        rule.setPattern(pattern);
        rule.setRegex(isRegex);
        rule.setSeverity(com.superchat.moderation.domain.FilterSeverity.valueOf(severity.toUpperCase()));
        rule.setAction(com.superchat.moderation.domain.FilterAction.valueOf(action.toUpperCase()));
        rule.setReplacement(replacement);
        return wordListRepository.save(rule);
    }

    @Transactional
    @CacheEvict(value = "moderationRules", key = "#orgId")
    public void deleteRule(UUID orgId, UUID ruleId) {
        WordList rule = wordListRepository.findById(ruleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));
        if (!rule.getOrgId().equals(orgId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Rule does not belong to this org");
        }
        wordListRepository.delete(rule);
    }

    @Transactional(readOnly = true)
    public List<WordList> listRules(UUID orgId) {
        return wordListRepository.findByOrgId(orgId);
    }

    // --- Incident queries ---

    @Transactional(readOnly = true)
    public Page<ModerationIncident> listIncidents(UUID orgId, String userId, Pageable pageable) {
        if (userId != null) {
            return incidentRepository.findByOrgIdAndUserIdOrderByCreatedAtDesc(orgId, userId, pageable);
        }
        return incidentRepository.findByOrgIdOrderByCreatedAtDesc(orgId, pageable);
    }

    // --- Internal helpers ---

    @Cacheable(value = "moderationRules", key = "#orgId")
    public List<WordList> getCachedRules(UUID orgId) {
        return wordListRepository.findByOrgId(orgId);
    }

    private Pattern compile(WordList rule) {
        String expr = rule.isRegex()
                ? rule.getPattern()
                : Pattern.quote(rule.getPattern());
        return Pattern.compile(expr, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private void recordIncident(UUID orgId, String userId, Long conversationId,
                                 String pattern, FilterAction action) {
        ModerationIncident incident = new ModerationIncident();
        incident.setOrgId(orgId);
        incident.setUserId(userId);
        incident.setConversationId(conversationId);
        incident.setMatchedPattern(pattern);
        incident.setActionTaken(action);
        incidentRepository.save(incident);

        Counter.builder("superchat.moderation.incidents")
                .tag("action", action.name())
                .description("Total moderation incidents by action taken")
                .register(meterRegistry)
                .increment();
    }

    // --- Result type ---

    public record CheckResult(String verdict, String sanitizedContent, String matchedPattern) {
        static CheckResult pass(String content) { return new CheckResult("PASS", content, null); }
        static CheckResult block(String pattern) { return new CheckResult("BLOCK", null, pattern); }
        static CheckResult warn(String content, String pattern) { return new CheckResult("WARN", content, pattern); }
    }
}
