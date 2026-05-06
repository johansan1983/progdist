package com.superchat.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "room_members")
public class RoomMember {

    @EmbeddedId
    private RoomMemberId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("roomId")
    @JoinColumn(name = "room_id")
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role = MemberRole.MEMBER;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    void onCreate() {
        this.joinedAt = Instant.now();
    }

    public RoomMemberId getId() { return id; }
    public void setId(RoomMemberId id) { this.id = id; }
    public Room getRoom() { return room; }
    public void setRoom(Room room) { this.room = room; }
    public MemberRole getRole() { return role; }
    public void setRole(MemberRole role) { this.role = role; }
    public Instant getJoinedAt() { return joinedAt; }
    public UUID getUserId() { return id != null ? id.getUserId() : null; }
}
