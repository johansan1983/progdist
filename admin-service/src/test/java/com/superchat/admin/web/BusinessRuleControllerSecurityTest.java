package com.superchat.admin.web;

import com.superchat.admin.config.OrgAccess;
import com.superchat.admin.config.SecurityConfig;
import com.superchat.admin.domain.BusinessRule;
import com.superchat.admin.service.BusinessRuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer test proving @PreAuthorize role guards are enforced through the real
 * Spring Security filter chain — not just at the service layer.
 */
@WebMvcTest(BusinessRuleController.class)
@Import(SecurityConfig.class)
class BusinessRuleControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private BusinessRuleService service;

    // Required so the oauth2 resource-server config can wire without a real JWKS endpoint.
    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean(name = "orgAccess")
    private OrgAccess orgAccess;   // SpEL: @orgAccess.belongsTo(authentication, #orgId)

    private final String orgId = UUID.randomUUID().toString();
    private final String rulesPath = "/admin/organizations/" + orgId + "/rules";

    // ── GET is open to any authenticated user ────────────────────────────────

    @Test
    @WithMockUser(roles = "USER")
    void regular_user_can_read_rules() throws Exception {
        when(service.listRules(any())).thenReturn(List.of());
        mvc.perform(get(rulesPath)).andExpect(status().isOk());
    }

    // ── Write endpoints require ORG_ADMIN / PLATFORM_ADMIN ───────────────────

    @Test
    @WithMockUser(roles = "USER")
    void regular_user_cannot_update_rule() throws Exception {
        mvc.perform(put(rulesPath + "/dm_enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"false\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ORG_ADMIN")
    void org_admin_can_update_rule_of_their_own_org() throws Exception {
        when(orgAccess.belongsTo(any(), any())).thenReturn(true);   // caller belongs to this org
        BusinessRule rule = new BusinessRule();
        rule.setOrgId(UUID.fromString(orgId));
        rule.setRuleKey("dm_enabled");
        rule.setRuleValue("false");
        org.springframework.test.util.ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
        org.springframework.test.util.ReflectionTestUtils.setField(rule, "updatedAt", java.time.Instant.now());
        when(service.upsertRule(any(), any(), any())).thenReturn(rule);

        mvc.perform(put(rulesPath + "/dm_enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"false\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("dm_enabled"))
                .andExpect(jsonPath("$.value").value("false"));
    }

    @Test
    @WithMockUser(roles = "ORG_ADMIN")
    void org_admin_cannot_update_rule_of_another_org() throws Exception {
        // Cross-tenant IDOR: an ORG_ADMIN of a DIFFERENT org must be refused.
        when(orgAccess.belongsTo(any(), any())).thenReturn(false);
        mvc.perform(put(rulesPath + "/dm_enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"false\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void platform_admin_can_seed_defaults() throws Exception {
        when(service.seedDefaults(any())).thenReturn(List.of());
        mvc.perform(post(rulesPath + "/seed-defaults"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void regular_user_cannot_seed_defaults() throws Exception {
        mvc.perform(post(rulesPath + "/seed-defaults"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DEPT_ADMIN")
    void dept_admin_cannot_delete_rule() throws Exception {
        mvc.perform(delete(rulesPath + "/dm_enabled"))
                .andExpect(status().isForbidden());
    }
}
