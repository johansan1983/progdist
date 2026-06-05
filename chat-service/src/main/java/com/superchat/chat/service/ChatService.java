package com.superchat.chat.service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final ModerationClient moderationClient;
    private final AuditEventPublisher auditPublisher;
    private final BusinessRuleClient businessRuleClient;
    private final MessagePersistenceService messagePersistence;

    public ChatService(
            ConversationRepository conversationRepository,
            ChatMessageRepository chatMessageRepository,
            ModerationClient moderationClient,
            AuditEventPublisher auditPublisher,
            BusinessRuleClient businessRuleClient,
            MessagePersistenceService messagePersistence
    ) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.moderationClient = moderationClient;
        this.auditPublisher = auditPublisher;
        this.businessRuleClient = businessRuleClient;
        this.messagePersistence = messagePersistence;
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

    /**
     * Orchestrates a send: all remote IO (business-rule fetch, moderation) happens here, with
     * NO transaction held, so a slow downstream never ties up a DB connection. The atomic write
     * (message row + outbox event rows) is delegated to MessagePersistenceService; an OutboxRelay
     * then publishes the events to RabbitMQ with at-least-once delivery.
     */
    /** Outcome of a send, carrying the moderation verdict so the API can warn the sender. */
    public record SendResult(ChatMessage message, String moderationVerdict, String moderatedWord) {}

    public SendResult sendMessage(Long conversationId, String content, String sender, String senderName,
            String attachmentUrl, String attachmentType, boolean viewOnce, String orgId) {
        if (conversationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId is required");
        }

        if ((content == null || content.isBlank()) && (attachmentUrl == null || attachmentUrl.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content or attachment is required");
        }

        // Fetch org rules once (remote call, no DB connection held) and reuse for all checks.
        Map<String, String> rules = orgId != null ? businessRuleClient.getRules(orgId) : Map.of();

        // Business rules: working hours
        if ("true".equalsIgnoreCase(rules.get("working_hours_only"))) {
            try {
                ZoneId zone = ZoneId.of(rules.getOrDefault("working_hours_timezone", "UTC"));
                LocalTime now   = LocalTime.now(zone);
                LocalTime start = LocalTime.parse(rules.getOrDefault("working_hours_start", "08:00"));
                LocalTime end   = LocalTime.parse(rules.getOrDefault("working_hours_end", "18:00"));
                if (now.isBefore(start) || now.isAfter(end)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                            "Messaging outside working hours is disabled by your organization");
                }
            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                log.warn("[BusinessRules] working hours check failed: {}", e.getMessage());
            }
        }

        String finalContent = content;
        String verdict = "PASS";
        String moderatedWord = null;
        if (content != null && !content.isBlank()) {
            ModerationClient.CheckResult moderation = moderationClient.check(orgId, sender, conversationId, content);
            if ("BLOCK".equals(moderation.verdict())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Message blocked by content policy");
            }
            verdict = moderation.verdict();
            finalContent = moderation.sanitizedContent();
            moderatedWord = moderation.matchedPattern();
        }

        // Atomic write: message row + outbox events commit together (no remote IO inside the tx).
        ChatMessage saved = messagePersistence.persist(
                conversationId, finalContent, sender, senderName, attachmentUrl, attachmentType, viewOnce, rules);

        auditPublisher.publish("MESSAGE_SENT", sender, String.valueOf(saved.getId()), orgId,
                Map.of("conversationId", conversationId, "senderName", senderName != null ? senderName : sender));

        return new SendResult(saved, verdict, moderatedWord);
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
            m.put("channelType", c.getChannelType() != null ? c.getChannelType() : "GENERAL");
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
