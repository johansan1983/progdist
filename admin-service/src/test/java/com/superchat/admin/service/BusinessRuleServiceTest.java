package com.superchat.admin.service;

import com.superchat.admin.domain.BusinessRule;
import com.superchat.admin.repo.BusinessRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BusinessRuleServiceTest {

    private BusinessRuleRepository repository;
    private BusinessRuleService service;

    @BeforeEach
    void setUp() {
        repository = mock(BusinessRuleRepository.class);
        service = new BusinessRuleService(repository);
    }

    // ── upsertRule ────────────────────────────────────────────────────────────

    @Test
    void upsertRule_creates_new_when_not_present() {
        UUID orgId = UUID.randomUUID();
        when(repository.findByOrgIdAndRuleKey(orgId, "dm_enabled")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BusinessRule result = service.upsertRule(orgId, "dm_enabled", "false");

        assertEquals("dm_enabled", result.getRuleKey());
        assertEquals("false", result.getRuleValue());
        assertEquals(orgId, result.getOrgId());
        verify(repository).save(any());
    }

    @Test
    void upsertRule_updates_value_when_already_present() {
        UUID orgId = UUID.randomUUID();
        BusinessRule existing = ruleFor(orgId, "max_file_size_mb", "10");
        when(repository.findByOrgIdAndRuleKey(orgId, "max_file_size_mb")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BusinessRule result = service.upsertRule(orgId, "max_file_size_mb", "50");

        assertEquals("50", result.getRuleValue());
        verify(repository).save(existing);
    }

    // ── getValue ──────────────────────────────────────────────────────────────

    @Test
    void getValue_returns_stored_value() {
        UUID orgId = UUID.randomUUID();
        BusinessRule rule = ruleFor(orgId, "working_hours_only", "true");
        when(repository.findByOrgIdAndRuleKey(orgId, "working_hours_only")).thenReturn(Optional.of(rule));

        assertEquals("true", service.getValue(orgId, "working_hours_only"));
    }

    @Test
    void getValue_falls_back_to_built_in_default_when_no_stored_rule() {
        UUID orgId = UUID.randomUUID();
        when(repository.findByOrgIdAndRuleKey(orgId, "dm_enabled")).thenReturn(Optional.empty());

        assertEquals("true", service.getValue(orgId, "dm_enabled"));
    }

    @Test
    void getValue_returns_null_for_unknown_key_with_no_stored_value() {
        UUID orgId = UUID.randomUUID();
        when(repository.findByOrgIdAndRuleKey(orgId, "unknown_key")).thenReturn(Optional.empty());

        assertNull(service.getValue(orgId, "unknown_key"));
    }

    // ── deleteRule ────────────────────────────────────────────────────────────

    @Test
    void deleteRule_removes_existing_rule() {
        UUID orgId = UUID.randomUUID();
        when(repository.findByOrgIdAndRuleKey(orgId, "guest_access_enabled"))
                .thenReturn(Optional.of(ruleFor(orgId, "guest_access_enabled", "false")));
        doNothing().when(repository).deleteByOrgIdAndRuleKey(orgId, "guest_access_enabled");

        assertDoesNotThrow(() -> service.deleteRule(orgId, "guest_access_enabled"));
        verify(repository).deleteByOrgIdAndRuleKey(orgId, "guest_access_enabled");
    }

    @Test
    void deleteRule_throws_404_when_rule_not_found() {
        UUID orgId = UUID.randomUUID();
        when(repository.findByOrgIdAndRuleKey(orgId, "missing")).thenReturn(Optional.empty());

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.deleteRule(orgId, "missing"));
    }

    // ── seedDefaults ──────────────────────────────────────────────────────────

    @Test
    void seedDefaults_saves_all_built_in_defaults_when_none_exist() {
        UUID orgId = UUID.randomUUID();
        when(repository.findByOrgIdAndRuleKey(any(), any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByOrgId(orgId)).thenReturn(List.of());

        service.seedDefaults(orgId);

        // Should have attempted to save one entry per default key
        verify(repository, times(BusinessRuleService.DEFAULTS.size())).save(any());
    }

    @Test
    void seedDefaults_does_not_overwrite_existing_rules() {
        UUID orgId = UUID.randomUUID();
        // All rules already exist
        when(repository.findByOrgIdAndRuleKey(any(), any()))
                .thenReturn(Optional.of(ruleFor(orgId, "any", "existing")));
        when(repository.findByOrgId(orgId)).thenReturn(List.of());

        service.seedDefaults(orgId);

        verify(repository, never()).save(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private BusinessRule ruleFor(UUID orgId, String key, String value) {
        BusinessRule r = new BusinessRule();
        r.setOrgId(orgId);
        r.setRuleKey(key);
        r.setRuleValue(value);
        return r;
    }
}
