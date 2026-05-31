package com.superchat.compliance.service;

import com.superchat.compliance.domain.AuditLog;
import com.superchat.compliance.domain.ConsentRecord;
import com.superchat.compliance.domain.ErasureRequest;
import com.superchat.compliance.repo.AuditLogRepository;
import com.superchat.compliance.repo.ConsentRecordRepository;
import com.superchat.compliance.repo.ErasureRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ComplianceService {

    private final AuditLogRepository auditLogRepository;
    private final ConsentRecordRepository consentRepository;
    private final ErasureRequestRepository erasureRepository;

    public ComplianceService(AuditLogRepository auditLogRepository,
                              ConsentRecordRepository consentRepository,
                              ErasureRequestRepository erasureRepository) {
        this.auditLogRepository = auditLogRepository;
        this.consentRepository = consentRepository;
        this.erasureRepository = erasureRepository;
    }

    // --- Audit log ---

    @Transactional(readOnly = true)
    public Page<AuditLog> queryAuditLog(UUID orgId, String actorId, Pageable pageable) {
        if (actorId != null) return auditLogRepository.findByActorIdOrderByCreatedAtDesc(actorId, pageable);
        if (orgId != null)   return auditLogRepository.findByOrgIdOrderByCreatedAtDesc(orgId, pageable);
        return auditLogRepository.findAll(pageable);
    }

    // --- Consent ---

    @Transactional
    public ConsentRecord recordConsent(String userId, UUID orgId, int version) {
        ConsentRecord record = consentRepository
                .findByUserIdAndOrgIdAndConsentVersion(userId, orgId, version)
                .orElseGet(() -> {
                    ConsentRecord r = new ConsentRecord();
                    r.setUserId(userId);
                    r.setOrgId(orgId);
                    r.setConsentVersion(version);
                    return r;
                });
        record.setAcceptedAt(Instant.now());
        record.setRevokedAt(null);
        return consentRepository.save(record);
    }

    @Transactional
    public ConsentRecord revokeConsent(String userId, UUID orgId) {
        ConsentRecord record = consentRepository.findActiveConsent(userId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No active consent found for user"));
        record.setRevokedAt(Instant.now());
        return consentRepository.save(record);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveConsent(String userId, UUID orgId) {
        return consentRepository.findActiveConsent(userId, orgId).isPresent();
    }

    // --- Erasure (GDPR right to be forgotten) ---

    @Transactional
    public ErasureRequest requestErasure(String userId, UUID orgId) {
        ErasureRequest req = new ErasureRequest();
        req.setUserId(userId);
        req.setOrgId(orgId);
        req.setStatus("PENDING");
        return erasureRepository.save(req);
    }

    @Transactional(readOnly = true)
    public List<ConsentRecord> listConsentHistory(String userId) {
        return consentRepository.findByUserIdOrderByConsentVersionDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<ErasureRequest> listErasureRequests(String userId) {
        return erasureRepository.findByUserIdOrderByRequestedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<ErasureRequest> listPendingErasureRequests() {
        return erasureRepository.findByStatus("PENDING");
    }

    @Transactional
    public ErasureRequest completeErasure(UUID requestId) {
        ErasureRequest req = erasureRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Erasure request not found"));
        req.setStatus("COMPLETED");
        req.setCompletedAt(Instant.now());
        return erasureRepository.save(req);
    }
}
