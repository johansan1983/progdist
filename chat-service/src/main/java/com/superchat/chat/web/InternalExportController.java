package com.superchat.chat.web;

import com.superchat.chat.domain.ChatMessage;
import com.superchat.chat.repo.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Internal endpoint for GDPR data export — no JWT, protected by shared internal token.
 * Accessible only within backend-net (not routed through API Gateway).
 */
@RestController
@RequestMapping("/internal/export")
public class InternalExportController {

    private final ChatMessageRepository messageRepository;
    private final String internalToken;

    public InternalExportController(ChatMessageRepository messageRepository,
                                     @Value("${internal.api.token:internal-secret}") String internalToken) {
        this.messageRepository = messageRepository;
        this.internalToken = internalToken;
    }

    @GetMapping("/messages/{userId}")
    public List<Map<String, Object>> exportMessages(
            @PathVariable String userId,
            @RequestHeader("X-Internal-Token") String token,
            @RequestParam(defaultValue = "1000") int limit) {

        if (!internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal token");
        }

        PageRequest page = PageRequest.of(0, Math.min(limit, 5000),
                Sort.by("createdAt").descending());

        return messageRepository.findBySenderOrderByCreatedAtDesc(userId, page)
                .getContent()
                .stream()
                .map(this::toMap)
                .toList();
    }

    @org.springframework.web.bind.annotation.PostMapping("/erase/{userId}")
    public Map<String, Object> eraseMessages(
            @PathVariable String userId,
            @RequestHeader("X-Internal-Token") String token) {

        if (!internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal token");
        }
        int affected = messageRepository.anonymizeBySender(userId);
        return Map.of("userId", userId, "anonymizedMessages", affected);
    }

    private Map<String, Object> toMap(ChatMessage m) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id", m.getId());
        map.put("conversationId", m.getConversation().getId());
        map.put("content", m.getContent() != null ? m.getContent() : "");
        map.put("attachmentUrl", m.getAttachmentUrl() != null ? m.getAttachmentUrl() : "");
        map.put("viewOnce", m.isViewOnce());
        map.put("createdAt", m.getCreatedAt().toString());
        return map;
    }
}
