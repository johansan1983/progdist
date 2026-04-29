package com.superchat.chat.web;

import java.util.List;
import java.util.Map;

import com.superchat.chat.domain.ChatMessage;
import com.superchat.chat.domain.Conversation;
import com.superchat.chat.security.AuthClient;
import com.superchat.chat.service.ChatService;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;
    private final AuthClient authClient;

    public ChatController(ChatService chatService, AuthClient authClient) {
        this.chatService = chatService;
        this.authClient = authClient;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "service", "chat-service",
                "status", "ok"
        );
    }

    @PostMapping("/conversations")
    public ResponseEntity<Map<String, Object>> createConversation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CreateConversationRequest request
    ) {
        String username = authClient.validateAndGetUsername(authorization);
        Conversation conversation = chatService.createConversation(request.name());

        return ResponseEntity.ok(Map.of(
                "id", conversation.getId(),
                "name", conversation.getName(),
                "createdAt", conversation.getCreatedAt().toString(),
                "createdBy", username
        ));
    }

    @PostMapping("/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody MessageRequest request
    ) {
        String username = authClient.validateAndGetUsername(authorization);
        ChatMessage saved = chatService.sendMessage(request.conversationId(), request.content(), username);

        return ResponseEntity.ok(Map.of(
                "id", saved.getId(),
                "conversationId", saved.getConversation().getId(),
                "sender", saved.getSender(),
                "content", saved.getContent(),
                "createdAt", saved.getCreatedAt().toString(),
                "status", "persisted_and_published"
        ));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<Map<String, Object>> listMessages(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long conversationId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size
    ) {
        authClient.validateAndGetUsername(authorization);

        Page<ChatMessage> paged = chatService.listMessages(conversationId, page, size);

        List<Map<String, Object>> messages = paged.getContent().stream()
                .map(message -> Map.<String, Object>of(
                        "id", message.getId(),
                        "conversationId", message.getConversation().getId(),
                        "sender", message.getSender(),
                        "content", message.getContent(),
                        "createdAt", message.getCreatedAt().toString()
                ))
                .toList();

        Map<String, Object> response = Map.of(
                "messages", messages,
                "page", paged.getNumber(),
                "size", paged.getSize(),
                "totalPages", paged.getTotalPages(),
                "totalElements", paged.getTotalElements()
        );

        return ResponseEntity.ok(response);
    }

    public record CreateConversationRequest(String name) {}
    public record MessageRequest(Long conversationId, String content) {}
}
