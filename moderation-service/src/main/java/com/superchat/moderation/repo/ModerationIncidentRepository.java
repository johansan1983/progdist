package com.superchat.moderation.repo;

import com.superchat.moderation.domain.ModerationIncident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ModerationIncidentRepository extends JpaRepository<ModerationIncident, UUID> {
    Page<ModerationIncident> findByOrgIdOrderByCreatedAtDesc(UUID orgId, Pageable pageable);
    Page<ModerationIncident> findByOrgIdAndUserIdOrderByCreatedAtDesc(UUID orgId, String userId, Pageable pageable);
}
