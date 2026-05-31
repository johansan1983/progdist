package com.superchat.notification.service;

import com.superchat.notification.domain.Notification;
import com.superchat.notification.repo.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private NotificationRepository repository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        service = new NotificationService(repository);
    }

    // ── listForUser ───────────────────────────────────────────────────────────

    @Test
    void listForUser_returns_page_for_recipient() {
        Page<Notification> page = new PageImpl<>(List.of(notification("alice", false)));
        when(repository.findByRecipientIdOrderByCreatedAtDesc(eq("alice"), any())).thenReturn(page);

        Page<Notification> result = service.listForUser("alice", 0, 50);

        assertEquals(1, result.getTotalElements());
        verify(repository).findByRecipientIdOrderByCreatedAtDesc(eq("alice"), any());
    }

    @Test
    void listForUser_caps_page_size_at_100() {
        when(repository.findByRecipientIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listForUser("alice", 0, 999);

        verify(repository).findByRecipientIdOrderByCreatedAtDesc(eq("alice"),
                argThat((Pageable p) -> p.getPageSize() == 100));
    }

    // ── countUnread ───────────────────────────────────────────────────────────

    @Test
    void countUnread_delegates_to_repository() {
        when(repository.countByRecipientIdAndIsReadFalse("alice")).thenReturn(7L);
        assertEquals(7L, service.countUnread("alice"));
    }

    // ── markRead ──────────────────────────────────────────────────────────────

    @Test
    void markRead_sets_read_flag_for_owner() {
        UUID id = UUID.randomUUID();
        Notification n = notification("alice", false);
        when(repository.findById(id)).thenReturn(Optional.of(n));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Notification result = service.markRead("alice", id);

        assertTrue(result.isRead());
        verify(repository).save(n);
    }

    @Test
    void markRead_throws_403_when_recipient_is_not_owner() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(notification("alice", false)));

        var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.markRead("mallory", id));
        assertEquals(403, ex.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test
    void markRead_throws_404_when_notification_missing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        var ex = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.markRead("alice", id));
        assertEquals(404, ex.getStatusCode().value());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Notification notification(String recipient, boolean read) {
        Notification n = new Notification();
        n.setRecipientId(recipient);
        n.setRead(read);
        return n;
    }
}
