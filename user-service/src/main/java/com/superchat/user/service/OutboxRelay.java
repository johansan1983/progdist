package com.superchat.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superchat.user.domain.OutboxEvent;
import com.superchat.user.repo.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Polls the outbox and publishes unsent room/membership events to RabbitMQ (at-least-once).
 * A row is marked published only after its broker publish succeeds; a failure leaves it
 * unpublished for the next tick. Consumers (chat-service projection) must be idempotent.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OutboxRelay(OutboxEventRepository outboxRepository,
                       RabbitTemplate rabbitTemplate,
                       ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${rooms.outbox.relay-interval-ms:2000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = outboxRepository.findTop100ByPublishedFalseOrderByIdAsc();
        if (batch.isEmpty()) return;
        for (OutboxEvent event : batch) {
            try {
                Map<String, Object> payload = objectMapper.readValue(event.getPayload(), MAP_TYPE);
                rabbitTemplate.convertAndSend(event.getExchange(), event.getRoutingKey(), payload);
                event.setPublished(true);
                event.setPublishedAt(Instant.now());
            } catch (Exception e) {
                log.error("[Outbox] failed to publish event id={} (will retry): {}", event.getId(), e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static final Class<Map<String, Object>> MAP_TYPE = (Class<Map<String, Object>>) (Class<?>) Map.class;
}
