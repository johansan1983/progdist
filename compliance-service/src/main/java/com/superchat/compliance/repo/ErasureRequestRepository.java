package com.superchat.compliance.repo;

import com.superchat.compliance.domain.ErasureRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ErasureRequestRepository extends JpaRepository<ErasureRequest, UUID> {
    List<ErasureRequest> findByUserIdOrderByRequestedAtDesc(String userId);
    List<ErasureRequest> findByStatus(String status);
    List<ErasureRequest> findByStatusIn(List<String> statuses);
}
