package com.superchat.compliance.repo;

import com.superchat.compliance.domain.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    @Query("SELECT c FROM ConsentRecord c WHERE c.userId = :userId AND c.orgId = :orgId " +
           "AND c.acceptedAt IS NOT NULL AND c.revokedAt IS NULL " +
           "ORDER BY c.consentVersion DESC")
    Optional<ConsentRecord> findActiveConsent(String userId, UUID orgId);

    Optional<ConsentRecord> findByUserIdAndOrgIdAndConsentVersion(String userId, UUID orgId, int version);

    List<ConsentRecord> findByUserIdOrderByConsentVersionDesc(String userId);
}
