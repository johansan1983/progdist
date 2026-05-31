package com.superchat.user.repo;

import com.superchat.user.domain.UserProfile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    Optional<UserProfile> findByKeycloakId(String keycloakId);
    List<UserProfile> findByDisplayNameContainingIgnoreCase(String query, Pageable pageable);
    List<UserProfile> findByOrganizationId(UUID orgId);
}
