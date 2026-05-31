package com.superchat.compliance.repo;

import com.superchat.compliance.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByOrgIdOrderByCreatedAtDesc(UUID orgId, Pageable pageable);
    Page<AuditLog> findByActorIdOrderByCreatedAtDesc(String actorId, Pageable pageable);
}
