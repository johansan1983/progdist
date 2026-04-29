package com.superchat.chat.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public ChatService(
            ConversationRepository conversationRepository,
            ChatMessageRepository chatMessageRepository,
            RabbitTemplate rabbitTemplate,
            @Value("${chat.rabbit.exchange}") String exchange,
            @Value("${chat.rabbit.routing-key}") String routingKey
    ) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
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
    public ChatMessage sendMessage(Long conversationId, String content, String sender) {
        if (conversationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId is required");
        }

        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        ChatMessage message = new ChatMessage();
        message.setConversation(conversation);
        message.setContent(content.trim());
        message.setSender(sender);

        ChatMessage saved = chatMessageRepository.save(message);

        Map<String, Object> event = Map.of(
                "eventType", "CHAT_MESSAGE_CREATED",
                "messageId", saved.getId(),
                "conversationId", conversationId,
                "sender", sender,
                "content", saved.getContent(),
                "createdAt", saved.getCreatedAt().toString(),
                "publishedAt", Instant.now().toString()
        );
        rabbitTemplate.convertAndSend(exchange, routingKey, event);

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<ChatMessage> listMessages(Long conversationId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        PageRequest pr = PageRequest.of(safePage, safeSize, Sort.by("createdAt").ascending());
        return chatMessageRepository.findByConversationId(conversationId, pr);
    }
}
