package com.superchat.user.web;

import com.superchat.user.domain.*;
import com.superchat.user.service.RoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/rooms")
public class RoomController {

    private final RoomService service;

    public RoomController(RoomService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createRoom(
            Authentication authentication,
            @RequestBody CreateRoomRequest request
    ) {
        RoomType type = request.type() != null ? RoomType.valueOf(request.type()) : RoomType.PUBLIC;
        ChannelType channelType = request.channelType() != null ? ChannelType.valueOf(request.channelType()) : ChannelType.GENERAL;
        java.util.UUID orgId = request.orgId() != null ? java.util.UUID.fromString(request.orgId()) : null;
        java.util.UUID deptId = request.deptId() != null ? java.util.UUID.fromString(request.deptId()) : null;
        Room room = service.createRoom(request.name(), request.description(), type, authentication.getName(), channelType, orgId, deptId);
        return ResponseEntity.ok(toMap(room));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listPublicRooms() {
        return ResponseEntity.ok(service.listPublicRooms().stream().map(this::toMap).toList());
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<Map<String, Object>>> listMembers(@PathVariable Long id) {
        return ResponseEntity.ok(service.listMembers(id).stream().map(this::memberToMap).toList());
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<Map<String, Object>> addMember(
            @PathVariable Long id,
            @RequestBody AddMemberRequest request
    ) {
        RoomMember member = service.addMember(id, UUID.fromString(request.userId()));
        return ResponseEntity.ok(memberToMap(member));
    }

    private Map<String, Object> toMap(Room r) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id", r.getId());
        map.put("name", r.getName());
        map.put("description", r.getDescription() != null ? r.getDescription() : "");
        map.put("type", r.getType().name());
        map.put("channelType", r.getChannelType().name());
        map.put("orgId", r.getOrganization() != null ? r.getOrganization().getId() : null);
        map.put("deptId", r.getDepartment() != null ? r.getDepartment().getId() : null);
        map.put("createdAt", r.getCreatedAt().toString());
        return map;
    }

    private Map<String, Object> memberToMap(RoomMember m) {
        return Map.of(
                "roomId", m.getId().getRoomId(),
                "userId", m.getId().getUserId(),
                "role", m.getRole().name(),
                "joinedAt", m.getJoinedAt().toString()
        );
    }

    public record CreateRoomRequest(String name, String description, String type, String channelType, String orgId, String deptId) {}
    public record AddMemberRequest(String userId) {}
}
