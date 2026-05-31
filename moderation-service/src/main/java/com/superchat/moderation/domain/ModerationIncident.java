package com.superchat.moderation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "moderation_incidents")
public class ModerationIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "matched_pattern", length = 500)
    private String matchedPattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_taken", nullable = false, length = 10)
    private FilterAction actionTaken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getOrgId() { return orgId; }
    public void setOrgId(UUID orgId) { this.orgId = orgId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getMatchedPattern() { return matchedPattern; }
    public void setMatchedPattern(String matchedPattern) { this.matchedPattern = matchedPattern; }
    public FilterAction getActionTaken() { return actionTaken; }
    public void setActionTaken(FilterAction actionTaken) { this.actionTaken = actionTaken; }
    public Instant getCreatedAt() { return createdAt; }
}
