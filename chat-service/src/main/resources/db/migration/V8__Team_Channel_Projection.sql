-- Team-channel projection in chat-service. user-service (Room) is the source of truth;
-- chat-service consumes rooms.exchange events and maintains this read model for messaging.
-- All columns are nullable/defaulted so existing conversations (DMs, legacy General) are unaffected.

ALTER TABLE conversation ADD COLUMN room_id     BIGINT;
ALTER TABLE conversation ADD COLUMN org_id      VARCHAR(36);
ALTER TABLE conversation ADD COLUMN dept_id     VARCHAR(36);
ALTER TABLE conversation ADD COLUMN visibility  VARCHAR(20);
ALTER TABLE conversation ADD COLUMN created_by  VARCHAR(64);
ALTER TABLE conversation ADD COLUMN archived    BOOLEAN NOT NULL DEFAULT FALSE;

-- One conversation per source room (when projected from a room).
CREATE UNIQUE INDEX uq_conversation_room_id ON conversation (room_id) WHERE room_id IS NOT NULL;

-- Membership projection for PRIVATE channels (PUBLIC channels are implicit by org/dept).
CREATE TABLE conversation_member (
    conversation_id BIGINT       NOT NULL REFERENCES conversation (id),
    user_id         VARCHAR(64)  NOT NULL,
    joined_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, user_id)
);
CREATE INDEX idx_conversation_member_user ON conversation_member (user_id);
