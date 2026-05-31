package com.superchat.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superchat.chat.domain.OutboxEvent;
import com.superchat.chat.repo.OutboxEventRepository;
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
 * Polls the outbox and publishes unsent events to RabbitMQ (at-least-once delivery).
 *
 * A row is marked published only after its broker publish succeeds; a failure leaves it
 * unpublished for the next run. Consumers must therefore be idempotent — a relay that
 * publishes then crashes before commit will re-publish that row, which is the accepted
 * at-least-once trade-off of the outbox pattern.
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

    @Scheduled(fixedDelayString = "${chat.outbox.relay-interval-ms:2000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = outboxRepository.findTop100ByPublishedFalseOrderByIdAsc();
        if (batch.isEmpty()) return;

        int sent = 0;
        for (OutboxEvent event : batch) {
            try {
                Map<String, Object> payload = objectMapper.readValue(event.getPayload(), MAP_TYPE);
                rabbitTemplate.convertAndSend(event.getExchange(), event.getRoutingKey(), payload);
                event.setPublished(true);
                event.setPublishedAt(Instant.now());
                sent++;
            } catch (Exception e) {
                // Leave unpublished; retried next tick. Don't abort the batch for one bad row.
                log.error("[Outbox] failed to publish event id={} (will retry): {}", event.getId(), e.getMessage());
            }
        }
        if (sent > 0) log.debug("[Outbox] published {}/{} events", sent, batch.size());
        // Managed entities flush on commit; published=true persists for the successful ones.
    }

    @SuppressWarnings("unchecked")
    private static final Class<Map<String, Object>> MAP_TYPE = (Class<Map<String, Object>>) (Class<?>) Map.class;
}
