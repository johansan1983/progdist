package com.superchat.chat.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversation")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 10)
    private String type = "GROUP";

    @Column(name = "dm_participant_a", length = 36)
    private String dmParticipantA;

    @Column(name = "dm_participant_b", length = 36)
    private String dmParticipantB;

    @Column(name = "dm_participant_a_name", length = 120)
    private String dmParticipantAName;

    @Column(name = "dm_participant_b_name", length = 120)
    private String dmParticipantBName;

    @Column(name = "channel_type", nullable = false, length = 20)
    private String channelType = "GENERAL";

    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "org_id", length = 36)
    private String orgId;

    @Column(name = "dept_id", length = 36)
    private String deptId;

    @Column(length = 20)
    private String visibility;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(nullable = false)
    private boolean archived = false;

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Instant getCreatedAt() { return createdAt; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDmParticipantA() { return dmParticipantA; }
    public void setDmParticipantA(String dmParticipantA) { this.dmParticipantA = dmParticipantA; }
    public String getDmParticipantB() { return dmParticipantB; }
    public void setDmParticipantB(String dmParticipantB) { this.dmParticipantB = dmParticipantB; }
    public String getDmParticipantAName() { return dmParticipantAName; }
    public void setDmParticipantAName(String dmParticipantAName) { this.dmParticipantAName = dmParticipantAName; }
    public String getDmParticipantBName() { return dmParticipantBName; }
    public void setDmParticipantBName(String dmParticipantBName) { this.dmParticipantBName = dmParticipantBName; }
    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }
    public String getDeptId() { return deptId; }
    public void setDeptId(String deptId) { this.deptId = deptId; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
}
