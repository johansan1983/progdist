package com.superchat.compliance.service;

import com.superchat.compliance.domain.ConsentRecord;
import com.superchat.compliance.domain.ErasureRequest;
import com.superchat.compliance.repo.AuditLogRepository;
import com.superchat.compliance.repo.ConsentRecordRepository;
import com.superchat.compliance.repo.ErasureRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ComplianceServiceTest {

    private AuditLogRepository auditRepo;
    private ConsentRecordRepository consentRepo;
    private ErasureRequestRepository erasureRepo;
    private ComplianceService service;

    private final String userId = UUID.randomUUID().toString();
    private final UUID orgId   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        auditRepo   = mock(AuditLogRepository.class);
        consentRepo = mock(ConsentRecordRepository.class);
        erasureRepo = mock(ErasureRequestRepository.class);
        service = new ComplianceService(auditRepo, consentRepo, erasureRepo);

        when(auditRepo.findByOrgIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(auditRepo.findByActorIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(consentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(erasureRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Consent ───────────────────────────────────────────────────────────────

    @Test
    void recordConsent_creates_new_record_when_none_exists() {
        when(consentRepo.findByUserIdAndOrgIdAndConsentVersion(userId, orgId, 1))
                .thenReturn(Optional.empty());

        ConsentRecord result = service.recordConsent(userId, orgId, 1);

        assertEquals(userId, result.getUserId());
        assertEquals(orgId, result.getOrgId());
        assertEquals(1, result.getConsentVersion());
        assertNotNull(result.getAcceptedAt());
        assertNull(result.getRevokedAt());
        verify(consentRepo).save(any());
    }

    @Test
    void recordConsent_updates_existing_record_and_clears_revocation() {
        ConsentRecord existing = consentRecord(userId, orgId, 1);
        existing.setRevokedAt(Instant.now().minusSeconds(3600));
        when(consentRepo.findByUserIdAndOrgIdAndConsentVersion(userId, orgId, 1))
                .thenReturn(Optional.of(existing));

        ConsentRecord result = service.recordConsent(userId, orgId, 1);

        assertNotNull(result.getAcceptedAt());
        assertNull(result.getRevokedAt());
        verify(consentRepo).save(existing);
    }

    @Test
    void revokeConsent_sets_revoked_at_timestamp() {
        ConsentRecord active = consentRecord(userId, orgId, 1);
        active.setAcceptedAt(Instant.now().minusSeconds(100));
        when(consentRepo.findActiveConsent(userId, orgId)).thenReturn(Optional.of(active));

        ConsentRecord result = service.revokeConsent(userId, orgId);

        assertNotNull(result.getRevokedAt());
        verify(consentRepo).save(active);
    }

    @Test
    void revokeConsent_throws_404_when_no_active_consent() {
        when(consentRepo.findActiveConsent(userId, orgId)).thenReturn(Optional.empty());

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.revokeConsent(userId, orgId));
    }

    @Test
    void hasActiveConsent_returns_true_when_active_record_exists() {
        when(consentRepo.findActiveConsent(userId, orgId))
                .thenReturn(Optional.of(consentRecord(userId, orgId, 1)));

        assertTrue(service.hasActiveConsent(userId, orgId));
    }

    @Test
    void hasActiveConsent_returns_false_when_no_active_record() {
        when(consentRepo.findActiveConsent(userId, orgId)).thenReturn(Optional.empty());

        assertFalse(service.hasActiveConsent(userId, orgId));
    }

    // ── Erasure ───────────────────────────────────────────────────────────────

    @Test
    void requestErasure_creates_pending_request() {
        ErasureRequest result = service.requestErasure(userId, orgId);

        assertEquals(userId, result.getUserId());
        assertEquals(orgId, result.getOrgId());
        assertEquals("PENDING", result.getStatus());
        verify(erasureRepo).save(any());
    }

    @Test
    void requestErasure_without_org_creates_pending_request_with_null_org() {
        ErasureRequest result = service.requestErasure(userId, null);

        assertEquals(userId, result.getUserId());
        assertNull(result.getOrgId());
        assertEquals("PENDING", result.getStatus());
    }

    @Test
    void completeErasure_sets_completed_status_and_timestamp() {
        UUID requestId = UUID.randomUUID();
        ErasureRequest pending = erasureRequest(userId, orgId, "PENDING");
        when(erasureRepo.findById(requestId)).thenReturn(Optional.of(pending));

        ErasureRequest result = service.completeErasure(requestId);

        assertEquals("COMPLETED", result.getStatus());
        assertNotNull(result.getCompletedAt());
        verify(erasureRepo).save(pending);
    }

    @Test
    void completeErasure_throws_404_when_request_not_found() {
        UUID requestId = UUID.randomUUID();
        when(erasureRepo.findById(requestId)).thenReturn(Optional.empty());

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.completeErasure(requestId));
    }

    @Test
    void listErasureRequests_returns_user_requests_in_order() {
        ErasureRequest r1 = erasureRequest(userId, orgId, "PENDING");
        ErasureRequest r2 = erasureRequest(userId, orgId, "COMPLETED");
        when(erasureRepo.findByUserIdOrderByRequestedAtDesc(userId)).thenReturn(List.of(r1, r2));

        List<ErasureRequest> result = service.listErasureRequests(userId);

        assertEquals(2, result.size());
        assertEquals("PENDING", result.get(0).getStatus());
    }

    // ── Audit log ─────────────────────────────────────────────────────────────

    @Test
    void queryAuditLog_filters_by_actorId_when_provided() {
        service.queryAuditLog(orgId, "actor-123", Pageable.unpaged());
        verify(auditRepo).findByActorIdOrderByCreatedAtDesc(eq("actor-123"), any());
        verify(auditRepo, never()).findByOrgIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void queryAuditLog_filters_by_orgId_when_no_actorId() {
        service.queryAuditLog(orgId, null, Pageable.unpaged());
        verify(auditRepo).findByOrgIdOrderByCreatedAtDesc(eq(orgId), any());
        verify(auditRepo, never()).findByActorIdOrderByCreatedAtDesc(any(), any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ConsentRecord consentRecord(String uid, UUID oid, int version) {
        ConsentRecord r = new ConsentRecord();
        r.setUserId(uid);
        r.setOrgId(oid);
        r.setConsentVersion(version);
        return r;
    }

    private ErasureRequest erasureRequest(String uid, UUID oid, String status) {
        ErasureRequest r = new ErasureRequest();
        r.setUserId(uid);
        r.setOrgId(oid);
        r.setStatus(status);
        return r;
    }
}
