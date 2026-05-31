package com.superchat.compliance.web;

import com.superchat.compliance.service.ComplianceService;
import com.superchat.compliance.service.DataExportClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.superchat.compliance.config.SecurityConfig;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Privacy boundary: a user may export ONLY their own data unless they are an admin.
 * This is enforced by @PreAuthorize("... or #userId == authentication.name") and can
 * only be verified through the real security filter chain — not at the service layer.
 */
@WebMvcTest(ComplianceController.class)
@Import(SecurityConfig.class)
class ComplianceExportSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean private ComplianceService service;
    @MockBean private DataExportClient exportClient;
    @MockBean private JwtDecoder jwtDecoder;

    private void stubEmptyExport() {
        when(exportClient.fetchProfile(any())).thenReturn(Map.of());
        when(exportClient.fetchMessages(any())).thenReturn(List.of());
        when(service.listConsentHistory(any())).thenReturn(List.of());
        when(service.listErasureRequests(any())).thenReturn(List.of());
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void user_can_export_own_data() throws Exception {
        stubEmptyExport();
        mvc.perform(get("/compliance/export/alice")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void user_cannot_export_another_users_data() throws Exception {
        mvc.perform(get("/compliance/export/bob")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ORG_ADMIN")
    void org_admin_can_export_any_users_data() throws Exception {
        stubEmptyExport();
        mvc.perform(get("/compliance/export/bob")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "viewer", roles = "USER")
    void user_cannot_read_audit_log() throws Exception {
        mvc.perform(get("/compliance/audit")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "PLATFORM_ADMIN")
    void platform_admin_can_read_audit_log() throws Exception {
        when(service.queryAuditLog(any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        mvc.perform(get("/compliance/audit")).andExpect(status().isOk());
    }
}
