package com.superchat.moderation.web;

import com.superchat.moderation.service.ModerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Internal endpoint called by chat-service before saving a message.
 * No JWT required — protected by network isolation (backend-net only).
 */
@RestController
@RequestMapping("/moderation/check")
public class ModerationCheckController {

    private final ModerationService service;

    public ModerationCheckController(ModerationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> check(@RequestBody CheckRequest req) {
        UUID orgId = req.orgId() != null ? UUID.fromString(req.orgId()) : null;
        if (orgId == null) {
            return ResponseEntity.ok(Map.of("verdict", "PASS", "sanitizedContent", req.content()));
        }
        ModerationService.CheckResult result = service.check(orgId, req.userId(),
                req.conversationId(), req.content());
        var resp = new java.util.LinkedHashMap<String, Object>();
        resp.put("verdict", result.verdict());
        resp.put("sanitizedContent", result.sanitizedContent() != null ? result.sanitizedContent() : "");
        resp.put("matchedPattern", result.matchedPattern() != null ? result.matchedPattern() : "");
        return ResponseEntity.ok(resp);
    }

    public record CheckRequest(String orgId, String userId, Long conversationId, String content) {}
}
