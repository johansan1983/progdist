package com.superchat.chat.web;

import java.util.List;
import java.util.Map;

import com.superchat.chat.domain.ChatMessage;
import com.superchat.chat.domain.Conversation;
import com.superchat.chat.service.ChatService;
import com.superchat.chat.service.ConversationAccessService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final ConversationAccessService conversationAccess;

    public ChatController(ChatService chatService, ConversationAccessService conversationAccess) {
        this.chatService = chatService;
        this.conversationAccess = conversationAccess;
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
                "channelType", conversation.getChannelType() != null ? conversation.getChannelType() : "GENERAL",
                "createdAt", conversation.getCreatedAt().toString(),
                "createdBy", username
        ));
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<Map<String, Object>>> listConversations(Authentication authentication) {
        String userId = authentication.getName();
        return ResponseEntity.ok(chatService.listConversationsForUser(userId));
    }

    @PostMapping("/conversations/dm")
    public ResponseEntity<Map<String, Object>> createDm(
            Authentication authentication,
            @RequestBody DmRequest request
    ) {
        if (request.participantId() == null || request.participantId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "participantId is required");
        }
        String myId = authentication.getName();
        String myName = preferredUsername(authentication);
        Conversation dm = chatService.createDm(myId, myName, request.participantId(),
                request.participantName() != null ? request.participantName() : request.participantId());
        boolean iAmA = myId.equals(dm.getDmParticipantA());
        return ResponseEntity.ok(Map.of(
                "id", dm.getId(),
                "type", dm.getType(),
                "otherParticipantName", iAmA ? dm.getDmParticipantBName() : dm.getDmParticipantAName(),
                "otherParticipantId", iAmA ? dm.getDmParticipantB() : dm.getDmParticipantA(),
                "createdAt", dm.getCreatedAt().toString()
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
            @RequestHeader(value = "X-Org-Id", required = false) String orgId,
            @RequestBody MessageRequest request
    ) {
        String sender = authentication.getName();
        String senderName = preferredUsername(authentication);
        log.info("[API] POST /chat/messages sender={} conversationId={}", senderName, request.conversationId());
        conversationAccess.assertCanAccess(request.conversationId(), authentication.getName());
        ChatMessage saved = chatService.sendMessage(
                request.conversationId(), request.content(), sender, senderName,
                request.attachmentUrl(), request.attachmentType(), request.viewOnce(), orgId);

        Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("id", saved.getId());
        resp.put("conversationId", saved.getConversation().getId());
        resp.put("sender", saved.getSender());
        resp.put("senderName", saved.getSenderName() != null ? saved.getSenderName() : saved.getSender());
        resp.put("content", saved.getContent() != null ? saved.getContent() : "");
        resp.put("attachmentUrl", saved.getAttachmentUrl() != null ? saved.getAttachmentUrl() : "");
        resp.put("attachmentType", saved.getAttachmentType() != null ? saved.getAttachmentType() : "");
        resp.put("createdAt", saved.getCreatedAt().toString());
        resp.put("viewOnce", saved.isViewOnce());
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
        conversationAccess.assertCanAccess(conversationId, authentication.getName());
        String currentUserId = authentication.getName();
        List<Map<String, Object>> messages = chatService.listMessages(conversationId, page, size, currentUserId);

        return ResponseEntity.ok(Map.of(
                "messages", messages,
                "page", page,
                "size", size
        ));
    }

    public record CreateConversationRequest(String name) {}
    public record MessageRequest(Long conversationId, String content, String attachmentUrl, String attachmentType, boolean viewOnce) {}
    public record DmRequest(String participantId, String participantName) {}
}
