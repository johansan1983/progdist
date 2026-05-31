package com.superchat.compliance.service;

import com.superchat.compliance.config.RabbitConfig;
import com.superchat.compliance.domain.ErasureRequest;
import com.superchat.compliance.repo.ErasureRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Nightly jobs for data lifecycle management.
 *
 * retention-check: publishes a RETENTION_CHECK audit event so downstream services
 *   can act on it. The actual message anonymisation lives in chat-service; this
 *   service records the intent and provides the org's configured retention window
 *   via the audit event payload.
 *
 * erasure-process: picks up PENDING erasure requests and publishes USER_ERASURE_REQUESTED
 *   events so chat-service and user-service can anonymise data for that user.
 */
@Component
public class RetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

    private final ErasureRequestRepository erasureRepository;
    private final RabbitTemplate rabbitTemplate;
    private final DataExportClient dataExportClient;

    public RetentionScheduler(ErasureRequestRepository erasureRepository,
                               RabbitTemplate rabbitTemplate,
                               DataExportClient dataExportClient) {
        this.erasureRepository = erasureRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.dataExportClient = dataExportClient;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void processErasureRequests() {
        // Reprocess FAILED requests too: the downstream anonymize endpoints are idempotent
        // (re-anonymizing an already-erased user is a no-op), so a transient failure on one
        // service self-heals on the next run instead of leaving a permanently partial erasure.
        List<ErasureRequest> pending = erasureRepository.findByStatusIn(List.of("PENDING", "FAILED"));
        log.info("[Retention] processing {} pending/failed erasure requests", pending.size());

        for (ErasureRequest req : pending) {
            try {
                req.setStatus("IN_PROGRESS");
                erasureRepository.save(req);

                // Execute the actual anonymization across services
                boolean erased = dataExportClient.eraseUserData(req.getUserId());

                req.setStatus(erased ? "COMPLETED" : "FAILED");
                req.setCompletedAt(java.time.Instant.now());
                erasureRepository.save(req);

                Map<String, Object> event = Map.of(
                        "eventType", erased ? "USER_ERASURE_COMPLETED" : "USER_ERASURE_FAILED",
                        "actorId", "compliance-service",
                        "targetId", req.getUserId(),
                        "orgId", req.getOrgId() != null ? req.getOrgId().toString() : "",
                        "payload", Map.of("erasureRequestId", String.valueOf(req.getId()),
                                          "userId", req.getUserId())
                );
                rabbitTemplate.convertAndSend(RabbitConfig.AUDIT_EXCHANGE, RabbitConfig.AUDIT_KEY, event);
                log.info("[Retention] erasure {} for userId={}",
                        erased ? "completed" : "FAILED", req.getUserId());
            } catch (Exception e) {
                log.error("[Retention] failed to process erasure requestId={}: {}",
                        req.getId(), e.getMessage(), e);
            }
        }
    }
}
