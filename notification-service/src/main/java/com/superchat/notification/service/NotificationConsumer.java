package com.superchat.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superchat.notification.domain.Notification;
import com.superchat.notification.domain.NotificationType;
import com.superchat.notification.repo.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationRepository repository;
    private final ObjectMapper objectMapper;

    public NotificationConsumer(NotificationRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${notifications.rabbit.queue:notifications.queue}")
    public void handleNotificationEvent(Map<String, Object> event) {
        try {
            String sender = String.valueOf(event.getOrDefault("sender", "unknown"));
            String payload = objectMapper.writeValueAsString(event);

            Notification notification = new Notification();
            notification.setRecipientId(sender);
            notification.setType(NotificationType.MESSAGE);
            notification.setPayload(payload);

            repository.save(notification);
        } catch (Exception e) {
            log.error("Failed to process notification event: {}", event, e);
        }
    }
}
