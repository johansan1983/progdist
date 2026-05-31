package com.superchat.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superchat.notification.domain.Notification;
import com.superchat.notification.domain.NotificationType;
import com.superchat.notification.repo.NotificationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationRepository repository;
    private final ObjectMapper objectMapper;
    private final Counter deliveredCounter;

    public NotificationConsumer(NotificationRepository repository, ObjectMapper objectMapper,
                                MeterRegistry meterRegistry) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.deliveredCounter = Counter.builder("superchat.notifications.delivered")
                .description("Total notifications persisted from RabbitMQ events")
                .register(meterRegistry);
    }

    @RabbitListener(queues = "${notifications.rabbit.queue:notifications.queue}")
    public void handleNotificationEvent(Map<String, Object> event,
                                        @Header(value = "X-Request-ID", required = false) String requestId) {
        if (requestId != null) {
            MDC.put("requestId", requestId);
        }
        try {
            String sender = String.valueOf(event.getOrDefault("sender", "unknown"));
            String payload = objectMapper.writeValueAsString(event);

            Notification notification = new Notification();
            notification.setRecipientId(sender);
            notification.setType(NotificationType.MESSAGE);
            notification.setPayload(payload);

            repository.save(notification);
            deliveredCounter.increment();
            log.info("[Worker] saved notification for sender={} messageId={}", sender, event.get("messageId"));
        } catch (Exception e) {
            log.error("Failed to process notification event: {}", event, e);
        } finally {
            MDC.remove("requestId");
        }
    }
}
