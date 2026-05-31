package com.superchat.compliance.web;

import com.superchat.compliance.domain.AuditLog;
import com.superchat.compliance.domain.ConsentRecord;
import com.superchat.compliance.domain.ErasureRequest;
import com.superchat.compliance.service.ComplianceService;
import com.superchat.compliance.service.DataExportClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/compliance")
public class ComplianceController {

    private final ComplianceService service;
    private final DataExportClient exportClient;

    public ComplianceController(ComplianceService service, DataExportClient exportClient) {
        this.service = service;
        this.exportClient = exportClient;
    }

    // --- GDPR Right of Access (data export) ---

    @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN') or #userId == authentication.name")
    @GetMapping("/export/{userId}")
    public ResponseEntity<Map<String, Object>> exportUserData(
            @PathVariable String userId,
            Authentication authentication) {

        var export = new java.util.LinkedHashMap<String, Object>();
        export.put("userId", userId);
        export.put("exportedAt", java.time.Instant.now().toString());
        export.put("profile",       exportClient.fetchProfile(userId));
        export.put("messages",      exportClient.fetchMessages(userId));
        export.put("consentHistory", service.listConsentHistory(userId));
        export.put("erasureRequests", service.listErasureRequests(userId)
                .stream().map(e -> {
                    var m = new java.util.LinkedHashMap<String, Object>();
                    m.put("id", e.getId());
                    m.put("status", e.getStatus());
                    m.put("requestedAt", e.getRequestedAt().toString());
                    m.put("completedAt", e.getCompletedAt() != null ? e.getCompletedAt().toString() : null);
                    return m;
                }).toList());

        return ResponseEntity.ok(export);
    }

    // --- Audit log ---

    @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN')")
    @GetMapping("/audit")
    public ResponseEntity<Map<String, Object>> queryAudit(
            @RequestParam(required = false) UUID orgId,
            @RequestParam(required = false) String actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        Page<Map<String, Object>> result = service.queryAuditLog(orgId, actorId, PageRequest.of(page, size))
                .map(this::auditToMap);
        return ResponseEntity.ok(Map.of(
                "content", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", result.getNumber()
        ));
    }

    // --- Consent ---

    @PostMapping("/consent")
    public ResponseEntity<Map<String, Object>> recordConsent(
            Authentication authentication,
            @RequestBody ConsentRequest req) {
        UUID orgId = UUID.fromString(req.orgId());
        ConsentRecord record = service.recordConsent(authentication.getName(), orgId, req.version());
        return ResponseEntity.ok(consentToMap(record));
    }

    @DeleteMapping("/consent/{orgId}")
    public ResponseEntity<Map<String, Object>> revokeConsent(
            Authentication authentication,
            @PathVariable UUID orgId) {
        ConsentRecord record = service.revokeConsent(authentication.getName(), orgId);
        return ResponseEntity.ok(consentToMap(record));
    }

    @GetMapping("/consent/{orgId}/status")
    public ResponseEntity<Map<String, Object>> consentStatus(
            Authentication authentication,
            @PathVariable UUID orgId) {
        boolean active = service.hasActiveConsent(authentication.getName(), orgId);
        return ResponseEntity.ok(Map.of("userId", authentication.getName(), "orgId", orgId, "active", active));
    }

    // --- Erasure (GDPR) ---

    @PostMapping("/erasure")
    public ResponseEntity<Map<String, Object>> requestErasure(
            Authentication authentication,
            @RequestBody ErasureRequest req) {
        UUID orgId = req.orgId() != null ? UUID.fromString(req.orgId()) : null;
        com.superchat.compliance.domain.ErasureRequest saved =
                service.requestErasure(authentication.getName(), orgId);
        return ResponseEntity.ok(erasureToMap(saved));
    }

    @GetMapping("/erasure")
    public ResponseEntity<List<Map<String, Object>>> listMyErasureRequests(Authentication authentication) {
        return ResponseEntity.ok(service.listErasureRequests(authentication.getName())
                .stream().map(this::erasureToMap).toList());
    }

    @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN')")
    @PutMapping("/erasure/{requestId}/complete")
    public ResponseEntity<Map<String, Object>> completeErasure(@PathVariable UUID requestId) {
        return ResponseEntity.ok(erasureToMap(service.completeErasure(requestId)));
    }

    // --- Mappers ---

    private Map<String, Object> auditToMap(AuditLog a) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id", a.getId());
        map.put("eventType", a.getEventType());
        map.put("actorId", a.getActorId());
        map.put("targetId", a.getTargetId() != null ? a.getTargetId() : "");
        map.put("orgId", a.getOrgId() != null ? a.getOrgId() : "");
        map.put("payload", a.getPayload() != null ? a.getPayload() : Map.of());
        map.put("createdAt", a.getCreatedAt().toString());
        return map;
    }

    private Map<String, Object> consentToMap(ConsentRecord c) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id", c.getId());
        map.put("userId", c.getUserId());
        map.put("orgId", c.getOrgId());
        map.put("consentVersion", c.getConsentVersion());
        map.put("acceptedAt", c.getAcceptedAt() != null ? c.getAcceptedAt().toString() : null);
        map.put("revokedAt", c.getRevokedAt() != null ? c.getRevokedAt().toString() : null);
        return map;
    }

    private Map<String, Object> erasureToMap(com.superchat.compliance.domain.ErasureRequest e) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id", e.getId());
        map.put("userId", e.getUserId());
        map.put("orgId", e.getOrgId() != null ? e.getOrgId() : "");
        map.put("status", e.getStatus());
        map.put("requestedAt", e.getRequestedAt().toString());
        map.put("completedAt", e.getCompletedAt() != null ? e.getCompletedAt().toString() : null);
        return map;
    }

    public record ConsentRequest(String orgId, int version) {}
    public record ErasureRequest(String orgId) {}
}
