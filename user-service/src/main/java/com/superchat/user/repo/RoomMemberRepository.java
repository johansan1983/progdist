package com.superchat.user.repo;

import com.superchat.user.domain.RoomMember;
import com.superchat.user.domain.RoomMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, RoomMemberId> {
    List<RoomMember> findByIdRoomId(Long roomId);
}
