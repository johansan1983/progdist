package com.superchat.user.web;

import com.superchat.user.domain.OnlineStatus;
import com.superchat.user.domain.UserProfile;
import com.superchat.user.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserProfileService service;

    public UserController(UserProfileService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyProfile(Authentication authentication) {
        UserProfile profile = service.getOrCreateProfile(authentication.getName(), preferredUsername(authentication));
        return ResponseEntity.ok(toMap(profile));
    }

    private static String preferredUsername(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            Object claim = jwt.getToken().getClaims().get("preferred_username");
            if (claim instanceof String s && !s.isBlank()) return s;
        }
        return authentication.getName();
    }

    @PutMapping("/me")
    public ResponseEntity<Map<String, Object>> updateMyProfile(
            Authentication authentication,
            @RequestBody UpdateProfileRequest request
    ) {
        OnlineStatus status = request.status() != null ? OnlineStatus.valueOf(request.status()) : null;
        UserProfile updated = service.updateProfile(
                authentication.getName(), request.displayName(), request.avatarUrl(), status, request.bio());
        return ResponseEntity.ok(toMap(updated));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(toMap(service.getById(id)));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> search(@RequestParam String q) {
        List<Map<String, Object>> results = service.search(q).stream()
                .map(this::toMap)
                .toList();
        return ResponseEntity.ok(results);
    }

    private Map<String, Object> toMap(UserProfile p) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id", p.getId());
        map.put("keycloakId", p.getKeycloakId());
        map.put("displayName", p.getDisplayName() != null ? p.getDisplayName() : "");
        map.put("avatarUrl", p.getAvatarUrl() != null ? p.getAvatarUrl() : "");
        map.put("status", p.getStatus().name());
        map.put("bio", p.getBio() != null ? p.getBio() : "");
        map.put("systemRole", p.getSystemRole().name());
        map.put("orgId", p.getOrganization() != null ? p.getOrganization().getId() : null);
        map.put("deptId", p.getDepartment() != null ? p.getDepartment().getId() : null);
        map.put("createdAt", p.getCreatedAt().toString());
        return map;
    }

    public record UpdateProfileRequest(String displayName, String avatarUrl, String status, String bio) {}
}
