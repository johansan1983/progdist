package com.superchat.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.superchat.chat.domain.Conversation;
import com.superchat.chat.repo.ChatMessageRepository;
import com.superchat.chat.repo.ConversationRepository;

class ConversationDmServiceTest {

    private ChatService chatService;
    private ConversationRepository conversationRepository;
    private ChatMessageRepository messageRepository;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        messageRepository = mock(ChatMessageRepository.class);
        chatService = new ChatService(
                conversationRepository, messageRepository, mock(RabbitTemplate.class),
                "chat.exchange", "chat.routing.key",
                "notifications.exchange", "notifications.message.created"
        );
    }

    @Test
    void testCreateDm_NewDm_SavesConversation() {
        when(conversationRepository.findDmBetween("alice-id", "bob-id")).thenReturn(Optional.empty());
        Conversation saved = dmConversation(1L, "alice-id", "alice", "bob-id", "bob");
        when(conversationRepository.save(any(Conversation.class))).thenReturn(saved);

        Conversation result = chatService.createDm("alice-id", "alice", "bob-id", "bob");

        assertEquals(1L, result.getId());
        assertEquals("DIRECT", result.getType());
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    void testCreateDm_ExistingDm_ReturnsExisting() {
        Conversation existing = dmConversation(5L, "alice-id", "alice", "bob-id", "bob");
        when(conversationRepository.findDmBetween("alice-id", "bob-id")).thenReturn(Optional.of(existing));

        Conversation result = chatService.createDm("alice-id", "alice", "bob-id", "bob");

        assertEquals(5L, result.getId());
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void testCreateDm_WithSelf_ThrowsBadRequest() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> chatService.createDm("alice-id", "alice", "alice-id", "alice"));
    }

    @Test
    void testListConversationsForUser_ReturnsGroupAndDm() {
        Conversation group = new Conversation();
        group.setId(1L); group.setName("General"); group.setType("GROUP");
        Conversation dm = dmConversation(2L, "alice-id", "alice", "bob-id", "bob");

        when(conversationRepository.findAllForUser("alice-id")).thenReturn(List.of(group, dm));

        List<Map<String, Object>> result = chatService.listConversationsForUser("alice-id");

        assertEquals(2, result.size());
        assertEquals("GROUP", result.get(0).get("type"));
        assertEquals("DIRECT", result.get(1).get("type"));
        assertEquals("bob", result.get(1).get("otherParticipantName"));
    }

    private Conversation dmConversation(Long id, String aId, String aName, String bId, String bName) {
        Conversation c = new Conversation();
        c.setId(id);
        c.setName("DM:" + aId + ":" + bId);
        c.setType("DIRECT");
        c.setDmParticipantA(aId);
        c.setDmParticipantAName(aName);
        c.setDmParticipantB(bId);
        c.setDmParticipantBName(bName);
        return c;
    }
}
