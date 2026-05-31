package com.superchat.moderation.web;

import com.superchat.moderation.domain.WordList;
import com.superchat.moderation.service.ModerationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/moderation/organizations/{orgId}")
public class WordListController {

    private final ModerationService service;

    public WordListController(ModerationService service) {
        this.service = service;
    }

    @GetMapping("/word-lists")
    public ResponseEntity<List<Map<String, Object>>> listRules(@PathVariable UUID orgId) {
        return ResponseEntity.ok(service.listRules(orgId).stream().map(this::ruleToMap).toList());
    }

    @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN')")
    @PostMapping("/word-lists")
    public ResponseEntity<Map<String, Object>> addRule(@PathVariable UUID orgId,
                                                        @RequestBody AddRuleRequest req) {
        WordList rule = service.addRule(orgId, req.pattern(), req.regex(),
                req.severity() != null ? req.severity() : "MEDIUM",
                req.action() != null ? req.action() : "BLOCK",
                req.replacement());
        return ResponseEntity.ok(ruleToMap(rule));
    }

    @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN')")
    @DeleteMapping("/word-lists/{ruleId}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID orgId, @PathVariable UUID ruleId) {
        service.deleteRule(orgId, ruleId);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN')")
    @GetMapping("/incidents")
    public ResponseEntity<Map<String, Object>> listIncidents(
            @PathVariable UUID orgId,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<Map<String, Object>> result = service.listIncidents(orgId, userId, PageRequest.of(page, size))
                .map(this::incidentToMap);
        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", result.getNumber()
        ));
    }

    private Map<String, Object> ruleToMap(WordList r) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id", r.getId());
        map.put("orgId", r.getOrgId());
        map.put("pattern", r.getPattern());
        map.put("regex", r.isRegex());
        map.put("severity", r.getSeverity().name());
        map.put("action", r.getAction().name());
        map.put("replacement", r.getReplacement() != null ? r.getReplacement() : "");
        map.put("createdAt", r.getCreatedAt().toString());
        return map;
    }

    private Map<String, Object> incidentToMap(com.superchat.moderation.domain.ModerationIncident i) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id", i.getId());
        map.put("orgId", i.getOrgId());
        map.put("userId", i.getUserId());
        map.put("conversationId", i.getConversationId());
        map.put("matchedPattern", i.getMatchedPattern());
        map.put("actionTaken", i.getActionTaken().name());
        map.put("createdAt", i.getCreatedAt().toString());
        return map;
    }

    public record AddRuleRequest(String pattern, boolean regex, String severity,
                                  String action, String replacement) {}
}
