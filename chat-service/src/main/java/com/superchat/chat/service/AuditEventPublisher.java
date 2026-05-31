package com.superchat.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AuditEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditEventPublisher.class);
    private static final String AUDIT_EXCHANGE = "audit.exchange";
    private static final String AUDIT_KEY      = "audit.event";

    private final RabbitTemplate rabbitTemplate;

    public AuditEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(String eventType, String actorId, String targetId, String orgId,
                        Map<String, Object> payload) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            event.put("actorId", actorId);
            event.put("targetId", targetId);
            event.put("orgId", orgId != null ? orgId : "");
            event.put("payload", payload);
            rabbitTemplate.convertAndSend(AUDIT_EXCHANGE, AUDIT_KEY, event);
        } catch (Exception e) {
            log.warn("[Audit] failed to publish event={}: {}", eventType, e.getMessage());
        }
    }
}
