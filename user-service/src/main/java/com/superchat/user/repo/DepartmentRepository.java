package com.superchat.user.repo;

import com.superchat.user.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    List<Department> findByOrganizationId(UUID orgId);
    List<Department> findByOrganizationIdAndParentDeptIsNull(UUID orgId);
}
