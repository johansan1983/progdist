package com.superchat.chat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superchat.chat.domain.ChatMessage;
import com.superchat.chat.domain.Conversation;
import com.superchat.chat.domain.OutboxEvent;
import com.superchat.chat.repo.ChatMessageRepository;
import com.superchat.chat.repo.ConversationRepository;
import com.superchat.chat.repo.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Owns the atomic write: the message row and its outbox event rows commit in ONE transaction.
 * No remote IO happens here (moderation/business-rule checks run in ChatService beforehand),
 * so the transaction is short and never holds a connection across a network call.
 *
 * A separate bean (not a private method on ChatService) so the @Transactional boundary is
 * applied through the Spring proxy rather than being lost to self-invocation.
 */
@Service
public class MessagePersistenceService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final String exchange;
    private final String routingKey;
    private final String notificationsExchange;
    private final String notificationsRoutingKey;

    public MessagePersistenceService(
            ConversationRepository conversationRepository,
            ChatMessageRepository chatMessageRepository,
            OutboxEventRepository outboxRepository,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${chat.rabbit.exchange}") String exchange,
            @Value("${chat.rabbit.routing-key}") String routingKey,
            @Value("${chat.notifications.exchange:notifications.exchange}") String notificationsExchange,
            @Value("${chat.notifications.routing-key:notifications.message.created}") String notificationsRoutingKey) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.notificationsExchange = notificationsExchange;
        this.notificationsRoutingKey = notificationsRoutingKey;
    }

    @Transactional
    public ChatMessage persist(Long conversationId, String finalContent, String sender, String senderName,
            String attachmentUrl, String attachmentType, boolean viewOnce, Map<String, String> rules) {

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        // Business rule: DM toggle
        if ("DIRECT".equals(conversation.getType()) && "false".equalsIgnoreCase(rules.get("dm_enabled"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Direct messaging is disabled by your organization");
        }

        // Channel permission: ANNOUNCEMENT channels are read-only for regular users
        if ("ANNOUNCEMENT".equals(conversation.getChannelType())) {
            Set<String> roles = getCurrentUserRoles();
            boolean isAdmin = roles.contains("ROLE_ORG_ADMIN") || roles.contains("ROLE_PLATFORM_ADMIN");
            if (!isAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only administrators can post to announcement channels");
            }
        }

        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        if (finalContent != null && !finalContent.isBlank()) {
            message.setContent(finalContent.trim());
        }
        if (attachmentUrl != null && !attachmentUrl.isBlank()) {
            message.setAttachmentUrl(attachmentUrl.trim());
            message.setAttachmentType(attachmentType);
        }
        message.setSender(sender);
        message.setSenderName(senderName);
        message.setViewOnce(viewOnce);

        ChatMessage saved = chatMessageRepository.save(message);

        Counter.builder("superchat.messages.sent")
                .tag("channel_type", conversation.getChannelType() != null ? conversation.getChannelType() : "GENERAL")
                .tag("conv_type", conversation.getType() != null ? conversation.getType() : "GROUP")
                .description("Total messages sent")
                .register(meterRegistry)
                .increment();

        // Outbox: same transaction as the message insert → atomic.
        String name = senderName != null ? senderName : sender;
        Map<String, Object> chatEvent = new HashMap<>();
        chatEvent.put("eventType", "CHAT_MESSAGE_CREATED");
        chatEvent.put("messageId", saved.getId());
        chatEvent.put("conversationId", conversationId);
        chatEvent.put("sender", sender);
        chatEvent.put("senderName", name);
        chatEvent.put("content", saved.getContent() != null ? saved.getContent() : "");
        chatEvent.put("createdAt", saved.getCreatedAt().toString());
        chatEvent.put("publishedAt", Instant.now().toString());
        chatEvent.put("attachmentUrl", saved.getAttachmentUrl() != null ? saved.getAttachmentUrl() : "");
        chatEvent.put("attachmentType", saved.getAttachmentType() != null ? saved.getAttachmentType() : "");

        Map<String, Object> notificationEvent = new HashMap<>();
        notificationEvent.put("eventType", "NOTIFICATION_EVENT");
        notificationEvent.put("type", "MESSAGE");
        notificationEvent.put("messageId", saved.getId());
        notificationEvent.put("conversationId", conversationId);
        notificationEvent.put("sender", sender);
        notificationEvent.put("senderName", name);
        notificationEvent.put("content", saved.getContent() != null ? saved.getContent() : "");
        notificationEvent.put("createdAt", saved.getCreatedAt().toString());

        String aggregateId = String.valueOf(saved.getId());
        outboxRepository.save(new OutboxEvent(aggregateId, exchange, routingKey, toJson(chatEvent)));
        outboxRepository.save(new OutboxEvent(aggregateId, notificationsExchange, notificationsRoutingKey, toJson(notificationEvent)));

        return saved;
    }

    private String toJson(Map<String, Object> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event", e);
        }
    }

    private Set<String> getCurrentUserRoles() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Set.of();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
