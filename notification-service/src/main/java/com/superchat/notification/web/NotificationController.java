package com.superchat.notification.web;

import com.superchat.notification.domain.Notification;
import com.superchat.notification.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<Notification> paged = service.listForUser(authentication.getName(), page, size);
        List<Map<String, Object>> items = paged.getContent().stream().map(this::toMap).toList();

        return ResponseEntity.ok(Map.of(
                "notifications", items,
                "page", paged.getNumber(),
                "totalPages", paged.getTotalPages(),
                "totalElements", paged.getTotalElements()
        ));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> unreadCount(Authentication authentication) {
        long count = service.countUnread(authentication.getName());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(
            Authentication authentication,
            @PathVariable UUID id
    ) {
        Notification n = service.markRead(authentication.getName(), id);
        return ResponseEntity.ok(toMap(n));
    }

    private Map<String, Object> toMap(Notification n) {
        return Map.of(
                "id", n.getId(),
                "recipientId", n.getRecipientId(),
                "type", n.getType().name(),
                "payload", n.getPayload() != null ? n.getPayload() : "",
                "isRead", n.isRead(),
                "createdAt", n.getCreatedAt().toString()
        );
    }
}
