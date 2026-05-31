package com.superchat.user.web;

import com.superchat.user.config.OrgAccess;
import com.superchat.user.config.SecurityConfig;
import com.superchat.user.domain.Organization;
import com.superchat.user.service.OrganizationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the two-tier admin hierarchy at the HTTP layer:
 *   - Creating/updating organizations is PLATFORM_ADMIN only.
 *   - Department + user management is open to ORG_ADMIN as well.
 */
@WebMvcTest(OrganizationController.class)
@Import(SecurityConfig.class)
class OrganizationControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean private OrganizationService service;
    @MockBean private JwtDecoder jwtDecoder;
    @MockBean(name = "orgAccess") private OrgAccess orgAccess;   // SpEL: @orgAccess.belongsTo(...)

    private final String orgId = UUID.randomUUID().toString();

    private Organization sampleOrg() {
        Organization o = new Organization();
        o.setName("Acme");
        o.setSlug("acme");
        ReflectionTestUtils.setField(o, "id", UUID.fromString(orgId));
        ReflectionTestUtils.setField(o, "createdAt", Instant.now());
        return o;
    }

    // ── Org creation: PLATFORM_ADMIN only ────────────────────────────────────

    @Test
    @WithMockUser(roles = "ORG_ADMIN")
    void org_admin_cannot_create_organization() throws Exception {
        mvc.perform(post("/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"slug\":\"acme\",\"plan\":\"ENTERPRISE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void platform_admin_can_create_organization() throws Exception {
        when(service.createOrganization(any(), any(), any())).thenReturn(sampleOrg());
        mvc.perform(post("/organizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme\",\"slug\":\"acme\",\"plan\":\"ENTERPRISE\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ORG_ADMIN")
    void org_admin_cannot_update_organization() throws Exception {
        mvc.perform(put("/organizations/" + orgId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isForbidden());
    }

    // ── Department + user management: ORG_ADMIN allowed ──────────────────────

    @Test
    @WithMockUser(roles = "ORG_ADMIN")
    void org_admin_can_list_users_of_their_own_org() throws Exception {
        when(orgAccess.belongsTo(any(), any())).thenReturn(true);   // caller belongs to this org
        when(service.listUsersByOrg(any())).thenReturn(List.of());
        mvc.perform(get("/organizations/" + orgId + "/users")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ORG_ADMIN")
    void org_admin_cannot_list_users_of_another_org() throws Exception {
        // The cross-tenant IDOR: an ORG_ADMIN of a DIFFERENT org must be refused.
        when(orgAccess.belongsTo(any(), any())).thenReturn(false);
        mvc.perform(get("/organizations/" + orgId + "/users")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ORG_ADMIN")
    void org_admin_cannot_assign_users_in_another_org() throws Exception {
        when(orgAccess.belongsTo(any(), any())).thenReturn(false);
        mvc.perform(put("/organizations/" + orgId + "/users/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"systemRole\":\"ORG_ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void platform_admin_can_list_any_org_users() throws Exception {
        // PLATFORM_ADMIN bypasses the per-org membership check.
        when(service.listUsersByOrg(any())).thenReturn(List.of());
        mvc.perform(get("/organizations/" + orgId + "/users")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void regular_user_cannot_list_org_users() throws Exception {
        mvc.perform(get("/organizations/" + orgId + "/users")).andExpect(status().isForbidden());
    }

    // ── Public reads stay open to any authenticated user ─────────────────────

    @Test
    @WithMockUser(roles = "USER")
    void any_user_can_list_organizations() throws Exception {
        when(service.listOrganizations()).thenReturn(List.of());
        mvc.perform(get("/organizations")).andExpect(status().isOk());
    }
}
