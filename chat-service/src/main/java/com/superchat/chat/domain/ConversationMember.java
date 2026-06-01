package com.superchat.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Projection of PRIVATE-channel membership, maintained from rooms.exchange events.
 * PUBLIC channels carry no rows here — their membership is implicit by org/dept.
 */
@Entity
@Table(name = "conversation_member")
public class ConversationMember {

    @EmbeddedId
    private ConversationMemberId id;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    public ConversationMember() {}

    public ConversationMember(ConversationMemberId id) {
        this.id = id;
    }

    @PrePersist
    void onCreate() {
        if (joinedAt == null) joinedAt = Instant.now();
    }

    public ConversationMemberId getId() { return id; }
    public void setId(ConversationMemberId id) { this.id = id; }
    public Instant getJoinedAt() { return joinedAt; }
}
