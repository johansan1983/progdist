package com.superchat.notification.service;

import com.superchat.notification.domain.Notification;
import com.superchat.notification.repo.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<Notification> listForUser(String recipientId, int page, int size) {
        int safeSize = Math.min(size, 100);
        return repository.findByRecipientIdOrderByCreatedAtDesc(recipientId, PageRequest.of(page, safeSize));
    }

    @Transactional(readOnly = true)
    public long countUnread(String recipientId) {
        return repository.countByRecipientIdAndIsReadFalse(recipientId);
    }

    @Transactional
    public Notification markRead(String recipientId, UUID notificationId) {
        Notification n = repository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        if (!n.getRecipientId().equals(recipientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your notification");
        }

        n.setRead(true);
        return repository.save(n);
    }
}
