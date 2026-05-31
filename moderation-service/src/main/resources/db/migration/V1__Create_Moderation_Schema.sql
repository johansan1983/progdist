CREATE TABLE word_lists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL,
    pattern VARCHAR(500) NOT NULL,
    is_regex BOOLEAN NOT NULL DEFAULT FALSE,
    severity VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',  -- LOW, MEDIUM, HIGH
    action VARCHAR(10) NOT NULL DEFAULT 'BLOCK',     -- BLOCK, REPLACE, WARN
    replacement TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_word_lists_org_id ON word_lists(org_id);

CREATE TABLE moderation_incidents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    conversation_id BIGINT,
    matched_pattern VARCHAR(500),
    action_taken VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_moderation_incidents_org_id ON moderation_incidents(org_id);
CREATE INDEX idx_moderation_incidents_user_id ON moderation_incidents(user_id);
CREATE INDEX idx_moderation_incidents_created_at ON moderation_incidents(created_at DESC);
