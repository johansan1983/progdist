package com.superchat.user.web;

import com.superchat.user.domain.*;
import com.superchat.user.service.OrganizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/organizations")
public class OrganizationController {

    private final OrganizationService service;

    public OrganizationController(OrganizationService service) {
        this.service = service;
    }

    // --- Organizations ---

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrg(@RequestBody CreateOrgRequest req) {
        OrgPlan plan = req.plan() != null ? OrgPlan.valueOf(req.plan()) : OrgPlan.BASIC;
        return ResponseEntity.ok(orgToMap(service.createOrganization(req.name(), req.slug(), plan)));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listOrgs() {
        return ResponseEntity.ok(service.listOrganizations().stream().map(this::orgToMap).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getOrg(@PathVariable UUID id) {
        return ResponseEntity.ok(orgToMap(service.getOrganization(id)));
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateOrg(@PathVariable UUID id,
                                                          @RequestBody UpdateOrgRequest req) {
        OrgPlan plan = req.plan() != null ? OrgPlan.valueOf(req.plan()) : null;
        return ResponseEntity.ok(orgToMap(service.updateOrganization(id, req.name(), plan)));
    }

    // --- Departments ---

    @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN')")
    @PostMapping("/{orgId}/departments")
    public ResponseEntity<Map<String, Object>> createDept(@PathVariable UUID orgId,
                                                           @RequestBody CreateDeptRequest req) {
        UUID parentId = req.parentDeptId() != null ? UUID.fromString(req.parentDeptId()) : null;
        return ResponseEntity.ok(deptToMap(service.createDepartment(orgId, req.name(), parentId)));
    }

    @GetMapping("/{orgId}/departments")
    public ResponseEntity<List<Map<String, Object>>> listDepts(@PathVariable UUID orgId,
                                                                @RequestParam(defaultValue = "false") boolean rootOnly) {
        List<Department> depts = rootOnly
                ? service.listRootDepartments(orgId)
                : service.listDepartments(orgId);
        return ResponseEntity.ok(depts.stream().map(this::deptToMap).toList());
    }

    // --- User Assignment ---

    @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN')")
    @PutMapping("/{orgId}/users/{userId}")
    public ResponseEntity<Map<String, Object>> assignUser(@PathVariable UUID orgId,
                                                           @PathVariable UUID userId,
                                                           @RequestBody AssignUserRequest req) {
        UUID deptId = req.deptId() != null ? UUID.fromString(req.deptId()) : null;
        SystemRole role = req.systemRole() != null ? SystemRole.valueOf(req.systemRole()) : null;
        UserProfile profile = service.assignUserToOrg(userId, orgId, deptId, role);
        return ResponseEntity.ok(userToMap(profile));
    }

    @PreAuthorize("hasAnyRole('ORG_ADMIN','PLATFORM_ADMIN')")
    @GetMapping("/{orgId}/users")
    public ResponseEntity<List<Map<String, Object>>> listUsers(@PathVariable UUID orgId) {
        return ResponseEntity.ok(service.listUsersByOrg(orgId).stream().map(this::userToMap).toList());
    }

    // --- Mappers ---

    private Map<String, Object> orgToMap(Organization o) {
        return Map.of(
                "id", o.getId(),
                "name", o.getName(),
                "slug", o.getSlug(),
                "plan", o.getPlan().name(),
                "createdAt", o.getCreatedAt().toString()
        );
    }

    private Map<String, Object> deptToMap(Department d) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id", d.getId());
        map.put("name", d.getName());
        map.put("orgId", d.getOrganization().getId());
        map.put("parentDeptId", d.getParentDept() != null ? d.getParentDept().getId() : null);
        map.put("createdAt", d.getCreatedAt().toString());
        return map;
    }

    private Map<String, Object> userToMap(UserProfile p) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id", p.getId());
        map.put("displayName", p.getDisplayName() != null ? p.getDisplayName() : "");
        map.put("systemRole", p.getSystemRole().name());
        map.put("orgId", p.getOrganization() != null ? p.getOrganization().getId() : null);
        map.put("deptId", p.getDepartment() != null ? p.getDepartment().getId() : null);
        return map;
    }

    public record CreateOrgRequest(String name, String slug, String plan) {}
    public record UpdateOrgRequest(String name, String plan) {}
    public record CreateDeptRequest(String name, String parentDeptId) {}
    public record AssignUserRequest(String deptId, String systemRole) {}
}
