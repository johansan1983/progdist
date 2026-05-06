package com.superchat.chat.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.superchat.chat.domain.ChatMessage;
import com.superchat.chat.domain.Conversation;
import com.superchat.chat.service.ChatService;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("service", "chat-service", "status", "ok");
    }

    @PostMapping("/conversations")
    public ResponseEntity<Map<String, Object>> createConversation(
            Authentication authentication,
            @RequestBody CreateConversationRequest request
    ) {
        String username = authentication.getName();
        Conversation conversation = chatService.createConversation(request.name());

        return ResponseEntity.ok(Map.of(
                "id", conversation.getId(),
                "name", conversation.getName(),
                "createdAt", conversation.getCreatedAt().toString(),
                "createdBy", username
        ));
    }

    private static String preferredUsername(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            Object claim = jwtToken.getToken().getClaims().get("preferred_username");
            if (claim instanceof String s && !s.isBlank()) return s;
        }
        return authentication.getName();
    }

    @PostMapping("/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            Authentication authentication,
            @RequestBody MessageRequest request
    ) {
        String sender = authentication.getName();
        String senderName = preferredUsername(authentication);
        ChatMessage saved = chatService.sendMessage(
                request.conversationId(), request.content(), sender, senderName,
                request.attachmentUrl(), request.attachmentType());

        Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("id", saved.getId());
        resp.put("conversationId", saved.getConversation().getId());
        resp.put("sender", saved.getSender());
        resp.put("senderName", saved.getSenderName() != null ? saved.getSenderName() : saved.getSender());
        resp.put("content", saved.getContent() != null ? saved.getContent() : "");
        resp.put("attachmentUrl", saved.getAttachmentUrl() != null ? saved.getAttachmentUrl() : "");
        resp.put("attachmentType", saved.getAttachmentType() != null ? saved.getAttachmentType() : "");
        resp.put("createdAt", saved.getCreatedAt().toString());
        resp.put("status", "persisted_and_published");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<Map<String, Object>> listMessages(
            Authentication authentication,
            @PathVariable Long conversationId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        Page<ChatMessage> paged = chatService.listMessages(conversationId, page, size);

        List<Map<String, Object>> messages = paged.getContent().stream()
                .map(message -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", message.getId());
                    m.put("conversationId", message.getConversation().getId());
                    m.put("sender", message.getSender());
                    m.put("senderName", message.getSenderName() != null ? message.getSenderName() : message.getSender());
                    m.put("content", message.getContent() != null ? message.getContent() : "");
                    m.put("attachmentUrl", message.getAttachmentUrl() != null ? message.getAttachmentUrl() : "");
                    m.put("attachmentType", message.getAttachmentType() != null ? message.getAttachmentType() : "");
                    m.put("createdAt", message.getCreatedAt().toString());
                    return m;
                })
                .toList();

        return ResponseEntity.ok(Map.of(
                "messages", messages,
                "page", paged.getNumber(),
                "size", paged.getSize(),
                "totalPages", paged.getTotalPages(),
                "totalElements", paged.getTotalElements()
        ));
    }

    public record CreateConversationRequest(String name) {}
    public record MessageRequest(Long conversationId, String content, String attachmentUrl, String attachmentType) {}
}
