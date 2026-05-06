package com.superchat.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.superchat.chat.domain.ChatMessage;
import com.superchat.chat.domain.Conversation;
import com.superchat.chat.repo.ChatMessageRepository;
import com.superchat.chat.repo.ConversationRepository;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

class ChatServiceTest {

    private ChatService chatService;
    private ChatMessageRepository messageRepository;
    private ConversationRepository conversationRepository;
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        messageRepository = mock(ChatMessageRepository.class);
        conversationRepository = mock(ConversationRepository.class);
        rabbitTemplate = mock(RabbitTemplate.class);

        chatService = new ChatService(
                conversationRepository,
                messageRepository,
                rabbitTemplate,
                "chat.exchange",
                "chat.routing.key",
                "notifications.exchange",
                "notifications.message.created"
        );
    }

    @Test
    void testListMessages_FirstPage_ReturnsPaginatedMessages() {
        Long conversationId = 1L;
        ChatMessage msg1 = new ChatMessage();
        msg1.setId(1L);
        msg1.setSender("user1");
        msg1.setContent("Hello");
        msg1.setCreatedAt(Instant.now());

        Conversation conv = new Conversation();
        conv.setId(conversationId);
        conv.setName("test");
        msg1.setConversation(conv);

        ChatMessage msg2 = new ChatMessage();
        msg2.setId(2L);
        msg2.setSender("user2");
        msg2.setContent("Hi there");
        msg2.setCreatedAt(Instant.now());
        msg2.setConversation(conv);

        Page<ChatMessage> page = new PageImpl<>(java.util.List.of(msg1, msg2));
        when(messageRepository.findByConversationId(eq(conversationId), any(Pageable.class))).thenReturn(page);

        List<Map<String, Object>> result = chatService.listMessages(conversationId, 0, 50, "any-user");

        assertEquals(2, result.size());
        assertEquals("user1", result.get(0).get("sender"));
        assertEquals("user2", result.get(1).get("sender"));
    }

    @Test
    void testListMessages_LimitMaxSize_CapsSizeAt200() {
        Long conversationId = 1L;
        Page<ChatMessage> emptyPage = new PageImpl<>(java.util.List.of());
        when(messageRepository.findByConversationId(eq(conversationId), any(Pageable.class))).thenReturn(emptyPage);

        chatService.listMessages(conversationId, 0, 500, "any-user");

        verify(messageRepository).findByConversationId(eq(conversationId), argThat(p -> p.getPageSize() == 200));
    }

    @Test
    void testListMessages_NegativePage_NormalizesToZero() {
        Long conversationId = 1L;
        Page<ChatMessage> emptyPage = new PageImpl<>(java.util.List.of());
        when(messageRepository.findByConversationId(eq(conversationId), any(Pageable.class))).thenReturn(emptyPage);

        chatService.listMessages(conversationId, -5, 50, "any-user");

        verify(messageRepository).findByConversationId(eq(conversationId), argThat(p -> p.getPageNumber() == 0));
    }

    @Test
    void testListMessages_DefaultSize_Uses50() {
        Long conversationId = 1L;
        Page<ChatMessage> emptyPage = new PageImpl<>(java.util.List.of());
        when(messageRepository.findByConversationId(eq(conversationId), any(Pageable.class))).thenReturn(emptyPage);

        chatService.listMessages(conversationId, 0, 50, "any-user");

        verify(messageRepository).findByConversationId(eq(conversationId), argThat(p -> p.getPageSize() == 50));
    }
}
