package com.superchat.moderation.web;

import com.superchat.moderation.config.SecurityConfig;
import com.superchat.moderation.service.ModerationService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WordListController.class)
@Import(SecurityConfig.class)
class WordListControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean private ModerationService service;
    @MockBean private JwtDecoder jwtDecoder;

    private final String orgId = UUID.randomUUID().toString();
    private final String base = "/moderation/organizations/" + orgId;

    @Test
    @WithMockUser(roles = "USER")
    void regular_user_can_read_word_lists() throws Exception {
        when(service.listRules(any())).thenReturn(List.of());
        mvc.perform(get(base + "/word-lists")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void regular_user_cannot_add_word_rule() throws Exception {
        mvc.perform(post(base + "/word-lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pattern\":\"x\",\"regex\":false,\"severity\":\"HIGH\",\"action\":\"BLOCK\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ORG_ADMIN")
    void org_admin_can_add_word_rule() throws Exception {
        var rule = new com.superchat.moderation.domain.WordList();
        rule.setOrgId(UUID.fromString(orgId));
        rule.setPattern("x");
        rule.setSeverity(com.superchat.moderation.domain.FilterSeverity.HIGH);
        rule.setAction(com.superchat.moderation.domain.FilterAction.BLOCK);
        org.springframework.test.util.ReflectionTestUtils.setField(rule, "id", UUID.randomUUID());
        org.springframework.test.util.ReflectionTestUtils.setField(rule, "createdAt", java.time.Instant.now());
        when(service.addRule(any(), any(), anyBoolean(), any(), any(), any())).thenReturn(rule);

        mvc.perform(post(base + "/word-lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pattern\":\"x\",\"regex\":false,\"severity\":\"HIGH\",\"action\":\"BLOCK\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void regular_user_cannot_view_incidents() throws Exception {
        mvc.perform(get(base + "/incidents")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void regular_user_cannot_delete_word_rule() throws Exception {
        mvc.perform(delete(base + "/word-lists/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    private static boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }
}
