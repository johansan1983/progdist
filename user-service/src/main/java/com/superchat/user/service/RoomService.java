package com.superchat.user.service;

import com.superchat.user.domain.*;
import com.superchat.user.repo.DepartmentRepository;
import com.superchat.user.repo.OrganizationRepository;
import com.superchat.user.repo.RoomMemberRepository;
import com.superchat.user.repo.RoomRepository;
import com.superchat.user.repo.UserProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final UserProfileRepository profileRepository;
    private final OrganizationRepository orgRepository;
    private final DepartmentRepository deptRepository;

    public RoomService(RoomRepository roomRepository,
                       RoomMemberRepository memberRepository,
                       UserProfileRepository profileRepository,
                       OrganizationRepository orgRepository,
                       DepartmentRepository deptRepository) {
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.profileRepository = profileRepository;
        this.orgRepository = orgRepository;
        this.deptRepository = deptRepository;
    }

    @Transactional
    public Room createRoom(String name, String description, RoomType type, String creatorKeycloakId,
                           ChannelType channelType, UUID orgId, UUID deptId) {
        Room room = new Room();
        room.setName(name);
        room.setDescription(description);
        room.setType(type != null ? type : RoomType.PUBLIC);
        room.setChannelType(channelType != null ? channelType : ChannelType.GENERAL);
        if (orgId != null) {
            room.setOrganization(orgRepository.findById(orgId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found")));
        }
        if (deptId != null) {
            room.setDepartment(deptRepository.findById(deptId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found")));
        }
        Room saved = roomRepository.save(room);

        UserProfile creator = profileRepository.findByKeycloakId(creatorKeycloakId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Creator profile not found — call GET /users/me first"));

        RoomMember owner = new RoomMember();
        owner.setId(new RoomMemberId(saved.getId(), creator.getId()));
        owner.setRoom(saved);
        owner.setRole(MemberRole.OWNER);
        memberRepository.save(owner);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Room> listPublicRooms() {
        return roomRepository.findByType(RoomType.PUBLIC);
    }

    @Transactional(readOnly = true)
    public List<RoomMember> listMembers(Long roomId) {
        roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        return memberRepository.findByIdRoomId(roomId);
    }

    @Transactional
    public RoomMember addMember(Long roomId, UUID userId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        RoomMemberId memberId = new RoomMemberId(roomId, userId);
        if (memberRepository.existsById(memberId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member");
        }

        RoomMember member = new RoomMember();
        member.setId(memberId);
        member.setRoom(room);
        member.setRole(MemberRole.MEMBER);
        return memberRepository.save(member);
    }
}
