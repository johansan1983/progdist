package com.superchat.admin.repo;

import com.superchat.admin.domain.BusinessRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessRuleRepository extends JpaRepository<BusinessRule, UUID> {
    List<BusinessRule> findByOrgId(UUID orgId);
    Optional<BusinessRule> findByOrgIdAndRuleKey(UUID orgId, String ruleKey);
    void deleteByOrgIdAndRuleKey(UUID orgId, String ruleKey);
}
