package com.superchat.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.superchat.chat.domain.ChatMessage;
import com.superchat.chat.domain.Conversation;
import com.superchat.chat.repo.ChatMessageRepository;
import com.superchat.chat.repo.ConversationRepository;

class ChatServiceViewOnceTest {

    private ChatService chatService;
    private ChatMessageRepository messageRepository;
    private ConversationRepository conversationRepository;

    @BeforeEach
    void setUp() {
        messageRepository = mock(ChatMessageRepository.class);
        conversationRepository = mock(ConversationRepository.class);
        chatService = new ChatService(
                conversationRepository, messageRepository, mock(RabbitTemplate.class),
                "chat.exchange", "chat.routing.key",
                "notifications.exchange", "notifications.message.created"
        );
    }

    @Test
    void testListMessages_ViewOnceNotViewed_OtherUser_IncludesContentAndMarksViewed() {
        ChatMessage msg = viewOnceMessage(1L, "secret-sender-id", "Hello secret", false);
        Page<ChatMessage> page = new PageImpl<>(List.of(msg));
        when(messageRepository.findByConversationId(eq(1L), any(Pageable.class))).thenReturn(page);

        List<Map<String, Object>> result = chatService.listMessages(1L, 0, 50, "other-user-id");

        assertEquals(1, result.size());
        assertEquals("Hello secret", result.get(0).get("content"));
        assertFalse((Boolean) result.get(0).get("viewOnceExpired"));
        verify(messageRepository).markViewed(1L);
    }

    @Test
    void testListMessages_ViewOnceAlreadyViewed_OtherUser_ReturnsTombstone() {
        ChatMessage msg = viewOnceMessage(2L, "secret-sender-id", "Hello secret", true);
        Page<ChatMessage> page = new PageImpl<>(List.of(msg));
        when(messageRepository.findByConversationId(eq(1L), any(Pageable.class))).thenReturn(page);

        List<Map<String, Object>> result = chatService.listMessages(1L, 0, 50, "other-user-id");

        assertEquals(1, result.size());
        assertNull(result.get(0).get("content"));
        assertTrue((Boolean) result.get(0).get("viewOnceExpired"));
        verify(messageRepository, never()).markViewed(anyLong());
    }

    @Test
    void testListMessages_ViewOnce_SenderAlwaysSeesFull() {
        ChatMessage msg = viewOnceMessage(3L, "alice-id", "My own message", false);
        Page<ChatMessage> page = new PageImpl<>(List.of(msg));
        when(messageRepository.findByConversationId(eq(1L), any(Pageable.class))).thenReturn(page);

        List<Map<String, Object>> result = chatService.listMessages(1L, 0, 50, "alice-id");

        assertEquals("My own message", result.get(0).get("content"));
        assertFalse((Boolean) result.get(0).get("viewOnceExpired"));
        verify(messageRepository, never()).markViewed(anyLong());
    }

    @Test
    void testListMessages_NormalMessage_NeverMarkedViewed() {
        ChatMessage msg = normalMessage(4L, "user1", "normal");
        Page<ChatMessage> page = new PageImpl<>(List.of(msg));
        when(messageRepository.findByConversationId(eq(1L), any(Pageable.class))).thenReturn(page);

        List<Map<String, Object>> result = chatService.listMessages(1L, 0, 50, "any-user");

        assertEquals("normal", result.get(0).get("content"));
        verify(messageRepository, never()).markViewed(anyLong());
    }

    private ChatMessage viewOnceMessage(Long id, String sender, String content, boolean viewed) {
        Conversation conv = new Conversation();
        conv.setId(1L);
        conv.setName("test");

        ChatMessage msg = new ChatMessage();
        msg.setId(id);
        msg.setSender(sender);
        msg.setContent(content);
        msg.setCreatedAt(Instant.now());
        msg.setViewOnce(true);
        msg.setViewed(viewed);
        msg.setConversation(conv);
        return msg;
    }

    private ChatMessage normalMessage(Long id, String sender, String content) {
        Conversation conv = new Conversation();
        conv.setId(1L);
        conv.setName("test");

        ChatMessage msg = new ChatMessage();
        msg.setId(id);
        msg.setSender(sender);
        msg.setContent(content);
        msg.setCreatedAt(Instant.now());
        msg.setViewOnce(false);
        msg.setConversation(conv);
        return msg;
    }
}
