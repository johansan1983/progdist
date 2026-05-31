package com.superchat.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superchat.chat.domain.Conversation;
import com.superchat.chat.repo.ChatMessageRepository;
import com.superchat.chat.repo.ConversationRepository;
import com.superchat.chat.repo.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The ANNOUNCEMENT read-only guard and DM toggle now live in MessagePersistenceService.persist
 * (the transactional write boundary). These tests drive it directly.
 */
class AnnouncementChannelTest {

    private MessagePersistenceService persistence;
    private ConversationRepository conversationRepository;
    private ChatMessageRepository messageRepository;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        messageRepository      = mock(ChatMessageRepository.class);
        OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);

        when(messageRepository.save(any())).thenAnswer(inv -> {
            com.superchat.chat.domain.ChatMessage msg = inv.getArgument(0);
            if (msg.getCreatedAt() == null) msg.setCreatedAt(java.time.Instant.now());
            return msg;
        });

        persistence = new MessagePersistenceService(
                conversationRepository, messageRepository, outboxRepository,
                new ObjectMapper(), new SimpleMeterRegistry(),
                "chat.exchange", "chat.routing.key",
                "notifications.exchange", "notifications.message.created");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void regular_user_cannot_post_to_announcement_channel() {
        setRoles("ROLE_USER");
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(announcementConversation()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> persistence.persist(1L, "hello", "user-id", "User", null, null, false, Map.of()));

        assertEquals(403, ex.getStatusCode().value());
        assertTrue(ex.getReason().toLowerCase().contains("announcement"));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void org_admin_can_post_to_announcement_channel() {
        setRoles("ROLE_ORG_ADMIN");
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(announcementConversation()));

        assertDoesNotThrow(() ->
                persistence.persist(1L, "important update", "admin-id", "Admin", null, null, false, Map.of()));

        verify(messageRepository).save(any());
    }

    @Test
    void platform_admin_can_post_to_announcement_channel() {
        setRoles("ROLE_PLATFORM_ADMIN");
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(announcementConversation()));

        assertDoesNotThrow(() ->
                persistence.persist(1L, "platform notice", "padmin-id", "PAdmin", null, null, false, Map.of()));

        verify(messageRepository).save(any());
    }

    @Test
    void regular_user_can_post_to_general_channel() {
        setRoles("ROLE_USER");
        when(conversationRepository.findById(2L)).thenReturn(Optional.of(generalConversation()));

        assertDoesNotThrow(() ->
                persistence.persist(2L, "hello everyone", "user-id", "User", null, null, false, Map.of()));

        verify(messageRepository).save(any());
    }

    @Test
    void no_security_context_cannot_post_to_announcement_channel() {
        SecurityContextHolder.clearContext();
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(announcementConversation()));

        assertThrows(ResponseStatusException.class,
                () -> persistence.persist(1L, "test", "user-id", "User", null, null, false, Map.of()));
    }

    @Test
    void dm_disabled_rule_blocks_direct_message() {
        setRoles("ROLE_USER");
        Conversation dm = new Conversation();
        dm.setType("DIRECT");
        dm.setChannelType("GENERAL");
        when(conversationRepository.findById(3L)).thenReturn(Optional.of(dm));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> persistence.persist(3L, "hi", "user-id", "User", null, null, false, Map.of("dm_enabled", "false")));
        assertEquals(403, ex.getStatusCode().value());
        verify(messageRepository, never()).save(any());
    }

    private void setRoles(String... roles) {
        var authorities = List.of(roles).stream().map(SimpleGrantedAuthority::new).toList();
        var auth = new TestingAuthenticationToken("user", "pass", authorities);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Conversation announcementConversation() {
        Conversation c = new Conversation();
        c.setName("Announcements");
        c.setType("GROUP");
        c.setChannelType("ANNOUNCEMENT");
        return c;
    }

    private Conversation generalConversation() {
        Conversation c = new Conversation();
        c.setName("General");
        c.setType("GROUP");
        c.setChannelType("GENERAL");
        return c;
    }
}
