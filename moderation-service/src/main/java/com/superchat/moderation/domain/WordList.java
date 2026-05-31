package com.superchat.moderation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "word_lists")
public class WordList {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false, length = 500)
    private String pattern;

    @Column(name = "is_regex", nullable = false)
    private boolean regex = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FilterSeverity severity = FilterSeverity.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FilterAction action = FilterAction.BLOCK;

    @Column(columnDefinition = "TEXT")
    private String replacement;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getOrgId() { return orgId; }
    public void setOrgId(UUID orgId) { this.orgId = orgId; }
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
    public boolean isRegex() { return regex; }
    public void setRegex(boolean regex) { this.regex = regex; }
    public FilterSeverity getSeverity() { return severity; }
    public void setSeverity(FilterSeverity severity) { this.severity = severity; }
    public FilterAction getAction() { return action; }
    public void setAction(FilterAction action) { this.action = action; }
    public String getReplacement() { return replacement; }
    public void setReplacement(String replacement) { this.replacement = replacement; }
    public Instant getCreatedAt() { return createdAt; }
}
