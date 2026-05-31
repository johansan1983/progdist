package com.superchat.compliance.service;

import com.superchat.compliance.config.RabbitConfig;
import com.superchat.compliance.domain.AuditLog;
import com.superchat.compliance.repo.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditConsumer.class);

    private final AuditLogRepository repository;

    public AuditConsumer(AuditLogRepository repository) {
        this.repository = repository;
    }

    @RabbitListener(queues = RabbitConfig.AUDIT_QUEUE)
    public void onAuditEvent(Map<String, Object> event) {
        try {
            AuditLog entry = new AuditLog();
            entry.setEventType((String) event.getOrDefault("eventType", "UNKNOWN"));
            entry.setActorId((String) event.getOrDefault("actorId", "system"));
            entry.setTargetId((String) event.get("targetId"));

            Object orgIdRaw = event.get("orgId");
            if (orgIdRaw instanceof String s && !s.isBlank()) {
                entry.setOrgId(UUID.fromString(s));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            entry.setPayload(payload);

            repository.save(entry);
            log.debug("[Audit] saved event={} actor={}", entry.getEventType(), entry.getActorId());
        } catch (Exception e) {
            log.error("[Audit] failed to persist event: {}", e.getMessage(), e);
        }
    }
}
