package com.superchat.user.repo;

import com.superchat.user.domain.Room;
import com.superchat.user.domain.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByType(RoomType type);
}
