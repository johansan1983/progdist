package com.superchat.admin.service;

import com.superchat.admin.domain.BusinessRule;
import com.superchat.admin.repo.BusinessRuleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Known rule keys and their value types:
 *   message_retention_days   integer (e.g. "365")
 *   max_file_size_mb         integer (e.g. "25")
 *   allowed_file_types       comma-separated MIME prefixes (e.g. "image/,application/pdf")
 *   working_hours_only       boolean ("true"/"false")
 *   working_hours_start      HH:mm in org timezone (e.g. "08:00")
 *   working_hours_end        HH:mm in org timezone (e.g. "18:00")
 *   working_hours_timezone   IANA zone id (e.g. "America/Bogota")
 *   require_consent_on_join  boolean ("true"/"false")
 *   consent_version          integer (bump to force re-consent)
 *   dm_enabled               boolean ("true"/"false")
 *   guest_access_enabled     boolean ("true"/"false")
 */
@Service
public class BusinessRuleService {

    public static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("message_retention_days", "365"),
            Map.entry("max_file_size_mb", "25"),
            Map.entry("allowed_file_types", "image/,video/,application/pdf,application/zip"),
            Map.entry("working_hours_only", "false"),
            Map.entry("working_hours_start", "08:00"),
            Map.entry("working_hours_end", "18:00"),
            Map.entry("working_hours_timezone", "UTC"),
            Map.entry("require_consent_on_join", "true"),
            Map.entry("consent_version", "1"),
            Map.entry("dm_enabled", "true"),
            Map.entry("guest_access_enabled", "false")
    );

    private final BusinessRuleRepository repository;

    public BusinessRuleService(BusinessRuleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<BusinessRule> listRules(UUID orgId) {
        return repository.findByOrgId(orgId);
    }

    @Transactional(readOnly = true)
    public String getValue(UUID orgId, String key) {
        return repository.findByOrgIdAndRuleKey(orgId, key)
                .map(BusinessRule::getRuleValue)
                .orElse(DEFAULTS.getOrDefault(key, null));
    }

    @Transactional
    public BusinessRule upsertRule(UUID orgId, String key, String value) {
        BusinessRule rule = repository.findByOrgIdAndRuleKey(orgId, key)
                .orElseGet(() -> {
                    BusinessRule r = new BusinessRule();
                    r.setOrgId(orgId);
                    r.setRuleKey(key);
                    return r;
                });
        rule.setRuleValue(value);
        return repository.save(rule);
    }

    @Transactional
    public void deleteRule(UUID orgId, String key) {
        if (repository.findByOrgIdAndRuleKey(orgId, key).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found: " + key);
        }
        repository.deleteByOrgIdAndRuleKey(orgId, key);
    }

    @Transactional
    public List<BusinessRule> seedDefaults(UUID orgId) {
        DEFAULTS.forEach((key, value) -> {
            if (repository.findByOrgIdAndRuleKey(orgId, key).isEmpty()) {
                BusinessRule rule = new BusinessRule();
                rule.setOrgId(orgId);
                rule.setRuleKey(key);
                rule.setRuleValue(value);
                repository.save(rule);
            }
        });
        return repository.findByOrgId(orgId);
    }
}
