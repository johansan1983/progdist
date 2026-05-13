package com.superchat.chat.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.superchat.chat.domain.ChatMessage;
import com.superchat.chat.domain.Conversation;
import com.superchat.chat.repo.ChatMessageRepository;
import com.superchat.chat.repo.ConversationRepository;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;
    private final String notificationsExchange;
    private final String notificationsRoutingKey;

    public ChatService(
            ConversationRepository conversationRepository,
            ChatMessageRepository chatMessageRepository,
            RabbitTemplate rabbitTemplate,
            @Value("${chat.rabbit.exchange}") String exchange,
            @Value("${chat.rabbit.routing-key}") String routingKey,
            @Value("${chat.notifications.exchange:notifications.exchange}") String notificationsExchange,
            @Value("${chat.notifications.routing-key:notifications.message.created}") String notificationsRoutingKey
    ) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.notificationsExchange = notificationsExchange;
        this.notificationsRoutingKey = notificationsRoutingKey;
    }

    @Transactional
    public Conversation createConversation(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conversation name is required");
        }

        String normalizedName = name.trim();
        return conversationRepository.findFirstByNameIgnoreCaseOrderByIdAsc(normalizedName)
                .orElseGet(() -> {
                    Conversation conversation = new Conversation();
                    conversation.setName(normalizedName);
                    return conversationRepository.save(conversation);
                });
    }

    @Transactional
    public ChatMessage sendMessage(Long conversationId, String content, String sender, String senderName,
            String attachmentUrl, String attachmentType, boolean viewOnce) {
        if (conversationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId is required");
        }

        if ((content == null || content.isBlank()) && (attachmentUrl == null || attachmentUrl.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content or attachment is required");
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        if (content != null && !content.isBlank()) {
            message.setContent(content.trim());
        }
        if (attachmentUrl != null && !attachmentUrl.isBlank()) {
            message.setAttachmentUrl(attachmentUrl.trim());
            message.setAttachmentType(attachmentType);
        }
        message.setSender(sender);
        message.setSenderName(senderName);
        message.setViewOnce(viewOnce);

        ChatMessage saved = chatMessageRepository.save(message);

        Map<String, Object> chatEvent = new HashMap<>();
        chatEvent.put("eventType", "CHAT_MESSAGE_CREATED");
        chatEvent.put("messageId", saved.getId());
        chatEvent.put("conversationId", conversationId);
        chatEvent.put("sender", sender);
        chatEvent.put("senderName", senderName != null ? senderName : sender);
        chatEvent.put("content", saved.getContent() != null ? saved.getContent() : "");
        chatEvent.put("createdAt", saved.getCreatedAt().toString());
        chatEvent.put("publishedAt", Instant.now().toString());
        chatEvent.put("attachmentUrl", saved.getAttachmentUrl() != null ? saved.getAttachmentUrl() : "");
        chatEvent.put("attachmentType", saved.getAttachmentType() != null ? saved.getAttachmentType() : "");
        log.info("[Rabbit] published messageId={} to exchange={}", saved.getId(), exchange);
        rabbitTemplate.convertAndSend(exchange, routingKey, chatEvent);

        Map<String, Object> notificationEvent = new HashMap<>();
        notificationEvent.put("eventType", "NOTIFICATION_EVENT");
        notificationEvent.put("type", "MESSAGE");
        notificationEvent.put("messageId", saved.getId());
        notificationEvent.put("conversationId", conversationId);
        notificationEvent.put("sender", sender);
        notificationEvent.put("senderName", senderName != null ? senderName : sender);
        notificationEvent.put("content", saved.getContent() != null ? saved.getContent() : "");
        notificationEvent.put("createdAt", saved.getCreatedAt().toString());
        log.info("[Rabbit] published notification for messageId={} to exchange={}", saved.getId(), notificationsExchange);
        rabbitTemplate.convertAndSend(notificationsExchange, notificationsRoutingKey, notificationEvent);

        return saved;
    }

    @Transactional
    public Conversation createDm(String myId, String myName, String participantId, String participantName) {
        if (myId == null || myId.isBlank() || participantId == null || participantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User IDs are required");
        }
        if (myId.equals(participantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot create DM with yourself");
        }
        return conversationRepository.findDmBetween(myId, participantId)
                .orElseGet(() -> {
                    Conversation dm = new Conversation();
                    dm.setName("DM:" + myId + ":" + participantId);
                    dm.setType("DIRECT");
                    dm.setDmParticipantA(myId);
                    dm.setDmParticipantAName(myName);
                    dm.setDmParticipantB(participantId);
                    dm.setDmParticipantBName(participantName);
                    return conversationRepository.save(dm);
                });
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listConversationsForUser(String userId) {
        return conversationRepository.findAllForUser(userId).stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("type", c.getType());
            m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
            if ("DIRECT".equals(c.getType())) {
                boolean iAmA = userId.equals(c.getDmParticipantA());
                m.put("otherParticipantName", iAmA ? c.getDmParticipantBName() : c.getDmParticipantAName());
                m.put("otherParticipantId", iAmA ? c.getDmParticipantB() : c.getDmParticipantA());
            }
            return m;
        }).toList();
    }

    @Transactional(readOnly = true)
    public void assertDmAccess(Long conversationId, String userId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        if ("DIRECT".equals(conv.getType())) {
            boolean isParticipant = userId.equals(conv.getDmParticipantA()) || userId.equals(conv.getDmParticipantB());
            if (!isParticipant) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant of this conversation");
            }
        }
    }

    @Transactional
    public List<Map<String, Object>> listMessages(Long conversationId, int page, int size, String currentUserId) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        PageRequest pr = PageRequest.of(safePage, safeSize, Sort.by("createdAt").ascending());
        Page<ChatMessage> paged = chatMessageRepository.findByConversationId(conversationId, pr);

        return paged.getContent().stream().map(msg -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", msg.getId());
            m.put("conversationId", msg.getConversation().getId());
            m.put("sender", msg.getSender());
            m.put("senderName", msg.getSenderName() != null ? msg.getSenderName() : msg.getSender());
            m.put("attachmentUrl", msg.getAttachmentUrl() != null ? msg.getAttachmentUrl() : "");
            m.put("attachmentType", msg.getAttachmentType() != null ? msg.getAttachmentType() : "");
            m.put("createdAt", msg.getCreatedAt().toString());
            m.put("viewOnce", msg.isViewOnce());

            boolean isSender = msg.getSender().equals(currentUserId);

            if (msg.isViewOnce() && !isSender) {
                if (!msg.isViewed()) {
                    int updated = chatMessageRepository.markViewed(msg.getId());
                    if (updated > 0) {
                        m.put("content", msg.getContent() != null ? msg.getContent() : "");
                        m.put("viewOnceExpired", false);
                    } else {
                        m.put("content", null);
                        m.put("attachmentUrl", null);
                        m.put("viewOnceExpired", true);
                    }
                } else {
                    m.put("content", null);
                    m.put("attachmentUrl", null);
                    m.put("viewOnceExpired", true);
                }
            } else {
                m.put("content", msg.getContent() != null ? msg.getContent() : "");
                m.put("viewOnceExpired", false);
            }
            return m;
        }).toList();
    }
}
