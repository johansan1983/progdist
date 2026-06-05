package com.superchat.user.web;

import com.superchat.user.config.SecurityConfig;
import com.superchat.user.domain.Room;
import com.superchat.user.service.RoomService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Channel mutations (create, add/remove member, archive) are admin-only.
 */
@WebMvcTest(RoomController.class)
@Import(SecurityConfig.class)
class RoomControllerSecurityTest {

    @Autowired private MockMvc mvc;

    @MockBean private RoomService roomService;
    @MockBean private JwtDecoder jwtDecoder;

    private Room sampleRoom() {
        Room r = new Room();
        r.setName("general");
        ReflectionTestUtils.setField(r, "id", 1L);
        ReflectionTestUtils.setField(r, "createdAt", Instant.now());
        return r;
    }

    @Test
    @WithMockUser(roles = "USER")
    void regular_user_cannot_create_channel() throws Exception {
        mvc.perform(post("/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"general\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void platform_admin_can_create_channel() throws Exception {
        when(roomService.createRoom(any(), any(), any(), any(), any(), any(), any())).thenReturn(sampleRoom());
        mvc.perform(post("/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"general\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ORG_ADMIN")
    void org_admin_can_create_channel() throws Exception {
        when(roomService.createRoom(any(), any(), any(), any(), any(), any(), any())).thenReturn(sampleRoom());
        mvc.perform(post("/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"team\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void regular_user_cannot_remove_member() throws Exception {
        mvc.perform(delete("/rooms/1/members/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void regular_user_cannot_archive_channel() throws Exception {
        mvc.perform(delete("/rooms/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ORG_ADMIN")
    void org_admin_can_archive_channel() throws Exception {
        mvc.perform(delete("/rooms/1")).andExpect(status().isNoContent());
    }
}
