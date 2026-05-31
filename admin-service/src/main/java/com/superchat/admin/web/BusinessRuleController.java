package com.superchat.admin.web;

import com.superchat.admin.domain.BusinessRule;
import com.superchat.admin.service.BusinessRuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/organizations/{orgId}/rules")
public class BusinessRuleController {

    private final BusinessRuleService service;

    public BusinessRuleController(BusinessRuleService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listRules(@PathVariable UUID orgId) {
        return ResponseEntity.ok(service.listRules(orgId).stream().map(this::toMap).toList());
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (hasRole('ORG_ADMIN') and @orgAccess.belongsTo(authentication, #orgId))")
    @PutMapping("/{key}")
    public ResponseEntity<Map<String, Object>> upsertRule(@PathVariable UUID orgId,
                                                           @PathVariable String key,
                                                           @RequestBody RuleValueRequest req) {
        return ResponseEntity.ok(toMap(service.upsertRule(orgId, key, req.value())));
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (hasRole('ORG_ADMIN') and @orgAccess.belongsTo(authentication, #orgId))")
    @DeleteMapping("/{key}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID orgId, @PathVariable String key) {
        service.deleteRule(orgId, key);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN') or (hasRole('ORG_ADMIN') and @orgAccess.belongsTo(authentication, #orgId))")
    @PostMapping("/seed-defaults")
    public ResponseEntity<List<Map<String, Object>>> seedDefaults(@PathVariable UUID orgId) {
        return ResponseEntity.ok(service.seedDefaults(orgId).stream().map(this::toMap).toList());
    }

    private Map<String, Object> toMap(BusinessRule r) {
        return Map.of(
                "id", r.getId(),
                "orgId", r.getOrgId(),
                "key", r.getRuleKey(),
                "value", r.getRuleValue(),
                "updatedAt", r.getUpdatedAt().toString()
        );
    }

    public record RuleValueRequest(String value) {}
}
