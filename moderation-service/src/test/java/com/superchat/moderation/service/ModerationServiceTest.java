package com.superchat.moderation.service;

import com.superchat.moderation.domain.FilterAction;
import com.superchat.moderation.domain.FilterSeverity;
import com.superchat.moderation.domain.ModerationIncident;
import com.superchat.moderation.domain.WordList;
import com.superchat.moderation.repo.ModerationIncidentRepository;
import com.superchat.moderation.repo.WordListRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ModerationServiceTest {

    private WordListRepository wordListRepo;
    private ModerationIncidentRepository incidentRepo;
    private ModerationService service;

    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        wordListRepo  = mock(WordListRepository.class);
        incidentRepo  = mock(ModerationIncidentRepository.class);
        service = new ModerationService(wordListRepo, incidentRepo,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        when(incidentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── PASS ──────────────────────────────────────────────────────────────────

    @Test
    void pass_when_no_rules_configured() {
        when(wordListRepo.findByOrgId(orgId)).thenReturn(List.of());
        ModerationService.CheckResult result = service.check(orgId, "user1", 1L, "Hello world");
        assertEquals("PASS", result.verdict());
        assertEquals("Hello world", result.sanitizedContent());
    }

    @Test
    void pass_when_content_does_not_match_any_rule() {
        when(wordListRepo.findByOrgId(orgId)).thenReturn(List.of(blockRule("badword")));
        ModerationService.CheckResult result = service.check(orgId, "user1", 1L, "This is fine");
        assertEquals("PASS", result.verdict());
        verifyNoInteractions(incidentRepo);
    }

    @Test
    void pass_for_null_content() {
        ModerationService.CheckResult result = service.check(orgId, "user1", 1L, null);
        assertEquals("PASS", result.verdict());
        verifyNoInteractions(wordListRepo);
    }

    @Test
    void pass_for_blank_content() {
        ModerationService.CheckResult result = service.check(orgId, "user1", 1L, "   ");
        assertEquals("PASS", result.verdict());
        verifyNoInteractions(wordListRepo);
    }

    @Test
    void pass_when_orgId_is_null() {
        ModerationService.CheckResult result = service.check(null, "user1", 1L, "anything");
        assertEquals("PASS", result.verdict());
        verifyNoInteractions(wordListRepo);
    }

    // ── BLOCK ─────────────────────────────────────────────────────────────────

    @Test
    void block_when_content_contains_blocked_word() {
        when(wordListRepo.findByOrgId(orgId)).thenReturn(List.of(blockRule("forbidden")));
        ModerationService.CheckResult result = service.check(orgId, "u1", 1L, "This is forbidden content");
        assertEquals("BLOCK", result.verdict());
        assertNull(result.sanitizedContent());
    }

    @Test
    void block_is_case_insensitive() {
        when(wordListRepo.findByOrgId(orgId)).thenReturn(List.of(blockRule("BadWord")));
        assertEquals("BLOCK", service.check(orgId, "u1", 1L, "I said BADWORD clearly").verdict());
        assertEquals("BLOCK", service.check(orgId, "u1", 1L, "badword in lowercase").verdict());
    }

    @Test
    void block_records_incident() {
        when(wordListRepo.findByOrgId(orgId)).thenReturn(List.of(blockRule("profanity")));
        service.check(orgId, "alice", 42L, "contains profanity here");
        verify(incidentRepo).save(argThat(i ->
                i instanceof ModerationIncident inc &&
                "alice".equals(inc.getUserId()) &&
                Long.valueOf(42L).equals(inc.getConversationId()) &&
                FilterAction.BLOCK.equals(inc.getActionTaken())
        ));
    }

    @Test
    void block_on_first_matching_rule_when_multiple_rules_exist() {
        WordList first  = blockRule("stop");
        WordList second = blockRule("also");
        when(wordListRepo.findByOrgId(orgId)).thenReturn(List.of(first, second));

        ModerationService.CheckResult result = service.check(orgId, "u1", 1L, "stop right there and also leave");
        assertEquals("BLOCK", result.verdict());
        // Only one incident (first match wins)
        verify(incidentRepo, times(1)).save(any());
    }

    // ── REPLACE ───────────────────────────────────────────────────────────────

    @Test
    void replace_substitutes_matched_word_with_replacement_text() {
        when(wordListRepo.findByOrgId(orgId)).thenReturn(List.of(replaceRule("badword", "***")));
        ModerationService.CheckResult result = service.check(orgId, "u1", 1L, "this is badword content");
        assertEquals("PASS", result.verdict());
        assertEquals("this is *** content", result.sanitizedContent());
    }

    @Test
    void replace_uses_default_asterisks_when_no_replacement_configured() {
        WordList rule = replaceRule("dirty", null);
        rule.setReplacement(null);
        when(wordListRepo.findByOrgId(orgId)).thenReturn(List.of(rule));
        ModerationService.CheckResult result = service.check(orgId, "u1", 1L, "dirty word");
        assertTrue(result.sanitizedContent().contains("***"));
    }

    @Test
    void replace_all_occurrences_of_matched_word() {
        when(wordListRepo.findByOrgId(orgId)).thenReturn(List.of(replaceRule("x", "[X]")));
        String result = service.check(orgId, "u1", 1L, "x marks the x spot x").sanitizedContent();
        assertEquals("[X] marks the [X] spot [X]", result);
    }

    @Test
    void replace_records_incident() {
        when(wordListRepo.findByOrgId(orgId)).thenReturn(List.of(replaceRule("slur", "***")));
        service.check(orgId, "bob", 5L, "said slur in message");
        verify(incidentRepo).save(argThat(i ->
                i instanceof ModerationIncident inc &&
                FilterAction.REPLACE.equals(inc.getActionTaken())
        ));
    }

    @Test
    void multiple_replace_rules_applied_in_order() {
        when(wordListRepo.findByOrgId(orgId)).thenReturn(List.of(
                replaceRule("hello", "hi"),
                replaceRule("world", "earth")
        ));
        String result = service.check(orgId, "u1", 1L, "hello world").sanitizedContent();
        assertEquals("hi earth", result);
    }

    // ── WARN ──────────────────────────────────────────────────────────────────

    @Test
    void warn_passes_content_unchanged_but_records_incident() {
        when(wordListRepo.findByOrgId(orgId)).thenReturn(List.of(warnRule("suspicious")));
        ModerationService.CheckResult result = service.check(orgId, "u1", 1L, "this is suspicious content");
        assertEquals("PASS", result.verdict());
        assertEquals("this is suspicious content", result.sanitizedContent());
        verify(incidentRepo).save(argThat(i ->
                i instanceof ModerationIncident inc &&
                FilterAction.WARN.equals(inc.getActionTaken())
        ));
    }

    // ── Regex ─────────────────────────────────────────────────────────────────

    @Test
    void regex_rule_matches_pattern() {
        WordList rule = blockRule("\\b\\d{4}-\\d{4}-\\d{4}-\\d{4}\\b");
        rule.setRegex(true);
        when(wordListRepo.findByOrgId(orgId)).thenReturn(List.of(rule));
        assertEquals("BLOCK", service.check(orgId, "u1", 1L, "my card is 1234-5678-9012-3456").verdict());
    }

    @Test
    void regex_rule_does_not_match_when_pattern_not_present() {
        WordList rule = blockRule("\\bsecret\\b");
        rule.setRegex(true);
        when(wordListRepo.findByOrgId(orgId)).thenReturn(List.of(rule));
        assertEquals("PASS", service.check(orgId, "u1", 1L, "no sensitive data here").verdict());
    }

    @Test
    void literal_rule_does_not_interpret_regex_metacharacters() {
        // The pattern "a.b" as literal should NOT match "axb" (dot not treated as wildcard)
        WordList rule = blockRule("a.b");
        rule.setRegex(false);
        when(wordListRepo.findByOrgId(orgId)).thenReturn(List.of(rule));
        assertEquals("PASS", service.check(orgId, "u1", 1L, "axb is ok").verdict());
        assertEquals("BLOCK", service.check(orgId, "u1", 1L, "literal a.b here").verdict());
    }

    // ── Word list management ──────────────────────────────────────────────────

    @Test
    void addRule_persists_new_word_list_entry() {
        when(wordListRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        WordList result = service.addRule(orgId, "badword", false, "HIGH", "BLOCK", null);
        assertEquals(orgId, result.getOrgId());
        assertEquals("badword", result.getPattern());
        assertEquals(FilterSeverity.HIGH, result.getSeverity());
        assertEquals(FilterAction.BLOCK, result.getAction());
    }

    @Test
    void deleteRule_throws_404_for_unknown_rule() {
        UUID ruleId = UUID.randomUUID();
        when(wordListRepo.findById(ruleId)).thenReturn(java.util.Optional.empty());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.deleteRule(orgId, ruleId));
    }

    @Test
    void deleteRule_throws_403_when_rule_belongs_to_different_org() {
        UUID ruleId = UUID.randomUUID();
        WordList rule = blockRule("word");
        rule.setOrgId(UUID.randomUUID()); // different org
        when(wordListRepo.findById(ruleId)).thenReturn(java.util.Optional.of(rule));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.deleteRule(orgId, ruleId));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private WordList blockRule(String pattern) {
        return rule(pattern, FilterAction.BLOCK, null);
    }

    private WordList replaceRule(String pattern, String replacement) {
        WordList r = rule(pattern, FilterAction.REPLACE, replacement);
        r.setReplacement(replacement != null ? replacement : "***");
        return r;
    }

    private WordList warnRule(String pattern) {
        return rule(pattern, FilterAction.WARN, null);
    }

    private WordList rule(String pattern, FilterAction action, String replacement) {
        WordList r = new WordList();
        r.setOrgId(orgId);
        r.setPattern(pattern);
        r.setRegex(false);
        r.setSeverity(FilterSeverity.MEDIUM);
        r.setAction(action);
        r.setReplacement(replacement);
        return r;
    }
}
