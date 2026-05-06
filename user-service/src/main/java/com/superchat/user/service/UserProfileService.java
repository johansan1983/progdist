package com.superchat.user.service;

import com.superchat.user.domain.OnlineStatus;
import com.superchat.user.domain.UserProfile;
import com.superchat.user.repo.UserProfileRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class UserProfileService {

    private final UserProfileRepository repository;

    public UserProfileService(UserProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UserProfile getOrCreateProfile(String keycloakId, String preferredUsername) {
        return repository.findByKeycloakId(keycloakId).map(profile -> {
            // Auto-fix profiles where displayName was set to the keycloakId placeholder
            if (profile.getDisplayName() == null || profile.getDisplayName().equals(keycloakId)) {
                profile.setDisplayName(preferredUsername);
                return repository.save(profile);
            }
            return profile;
        }).orElseGet(() -> {
            UserProfile profile = new UserProfile();
            profile.setKeycloakId(keycloakId);
            profile.setDisplayName(preferredUsername);
            profile.setStatus(OnlineStatus.ONLINE);
            return repository.save(profile);
        });
    }

    @Transactional(readOnly = true)
    public UserProfile getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @Transactional
    public UserProfile updateProfile(String keycloakId, String displayName, String avatarUrl,
                                      OnlineStatus status, String bio) {
        UserProfile profile = repository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));

        if (displayName != null) profile.setDisplayName(displayName);
        if (avatarUrl != null) profile.setAvatarUrl(avatarUrl);
        if (status != null) profile.setStatus(status);
        if (bio != null) profile.setBio(bio);

        return repository.save(profile);
    }

    @Transactional(readOnly = true)
    public List<UserProfile> search(String query) {
        return repository.findByDisplayNameContainingIgnoreCase(query, PageRequest.of(0, 20));
    }
}
