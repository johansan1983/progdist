package com.superchat.user.web;

import com.superchat.user.domain.UserProfile;
import com.superchat.user.repo.UserProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

/**
 * Internal endpoint for GDPR data export — no JWT, protected by shared internal token.
 * Accessible only within backend-net (not routed through API Gateway).
 */
@RestController
@RequestMapping("/internal/export")
public class InternalExportController {

    private final UserProfileRepository profileRepository;
    private final String internalToken;

    public InternalExportController(UserProfileRepository profileRepository,
                                     @Value("${internal.api.token:internal-secret}") String internalToken) {
        this.profileRepository = profileRepository;
        this.internalToken = internalToken;
    }

    @GetMapping("/profile/{keycloakId}")
    public Map<String, Object> exportProfile(
            @PathVariable String keycloakId,
            @RequestHeader("X-Internal-Token") String token) {

        if (!internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal token");
        }

        Optional<UserProfile> profile = profileRepository.findByKeycloakId(keycloakId);
        if (profile.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        }

        UserProfile p = profile.get();
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id", p.getId());
        map.put("keycloakId", p.getKeycloakId());
        map.put("displayName", p.getDisplayName() != null ? p.getDisplayName() : "");
        map.put("bio", p.getBio() != null ? p.getBio() : "");
        map.put("status", p.getStatus().name());
        map.put("systemRole", p.getSystemRole().name());
        map.put("orgId", p.getOrganization() != null ? p.getOrganization().getId() : null);
        map.put("deptId", p.getDepartment() != null ? p.getDepartment().getId() : null);
        map.put("createdAt", p.getCreatedAt().toString());
        return map;
    }

    @org.springframework.web.bind.annotation.PostMapping("/erase/profile/{keycloakId}")
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> eraseProfile(
            @PathVariable String keycloakId,
            @RequestHeader("X-Internal-Token") String token) {

        if (!internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal token");
        }

        UserProfile p = profileRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));

        p.setDisplayName("[deleted]");
        p.setBio(null);
        p.setAvatarUrl(null);
        profileRepository.save(p);

        return Map.of("keycloakId", keycloakId, "anonymized", true);
    }
}
