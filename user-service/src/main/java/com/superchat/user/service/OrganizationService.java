package com.superchat.user.service;

import com.superchat.user.domain.*;
import com.superchat.user.repo.DepartmentRepository;
import com.superchat.user.repo.OrganizationRepository;
import com.superchat.user.repo.UserProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class OrganizationService {

    private final OrganizationRepository orgRepository;
    private final DepartmentRepository deptRepository;
    private final UserProfileRepository profileRepository;

    public OrganizationService(OrganizationRepository orgRepository,
                                DepartmentRepository deptRepository,
                                UserProfileRepository profileRepository) {
        this.orgRepository = orgRepository;
        this.deptRepository = deptRepository;
        this.profileRepository = profileRepository;
    }

    // --- Organizations ---

    @Transactional
    public Organization createOrganization(String name, String slug, OrgPlan plan) {
        if (orgRepository.existsBySlug(slug)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already in use: " + slug);
        }
        Organization org = new Organization();
        org.setName(name);
        org.setSlug(slug);
        org.setPlan(plan != null ? plan : OrgPlan.BASIC);
        return orgRepository.save(org);
    }

    @Transactional(readOnly = true)
    public List<Organization> listOrganizations() {
        return orgRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Organization getOrganization(UUID id) {
        return orgRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
    }

    @Transactional
    public Organization updateOrganization(UUID id, String name, OrgPlan plan) {
        Organization org = getOrganization(id);
        if (name != null) org.setName(name);
        if (plan != null) org.setPlan(plan);
        return orgRepository.save(org);
    }

    // --- Departments ---

    @Transactional
    public Department createDepartment(UUID orgId, String name, UUID parentDeptId) {
        Organization org = getOrganization(orgId);
        Department dept = new Department();
        dept.setOrganization(org);
        dept.setName(name);
        if (parentDeptId != null) {
            Department parent = deptRepository.findById(parentDeptId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent department not found"));
            dept.setParentDept(parent);
        }
        return deptRepository.save(dept);
    }

    @Transactional(readOnly = true)
    public List<Department> listDepartments(UUID orgId) {
        getOrganization(orgId);
        return deptRepository.findByOrganizationId(orgId);
    }

    @Transactional(readOnly = true)
    public List<Department> listRootDepartments(UUID orgId) {
        getOrganization(orgId);
        return deptRepository.findByOrganizationIdAndParentDeptIsNull(orgId);
    }

    // --- User Assignment ---

    @Transactional
    public UserProfile assignUserToOrg(UUID userId, UUID orgId, UUID deptId, SystemRole role) {
        UserProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Organization org = getOrganization(orgId);
        profile.setOrganization(org);
        if (deptId != null) {
            Department dept = deptRepository.findById(deptId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
            profile.setDepartment(dept);
        }
        if (role != null) profile.setSystemRole(role);
        return profileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public List<UserProfile> listUsersByOrg(UUID orgId) {
        getOrganization(orgId);
        return profileRepository.findByOrganizationId(orgId);
    }
}
