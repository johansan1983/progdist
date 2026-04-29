-- V1__Initial_Schema.sql
-- Initial database schema with optimized indices for SuperChat

CREATE TABLE IF NOT EXISTS conversation (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_conversation_created_at ON conversation(created_at DESC);

CREATE TABLE IF NOT EXISTS chat_message (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  sender VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_chat_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE
);

-- Primary query: Get messages for a conversation (pagination)
-- SELECT * FROM chat_message WHERE conversation_id = ? ORDER BY created_at DESC LIMIT 50 OFFSET ?
CREATE INDEX idx_chat_message_conversation_created ON chat_message(conversation_id, created_at DESC);

-- Secondary query: Find recent messages across all conversations
CREATE INDEX idx_chat_message_created_at ON chat_message(created_at DESC);

-- Query optimization: Count messages per conversation
CREATE INDEX idx_chat_message_conversation_id ON chat_message(conversation_id);

-- Optional: Support searching by sender
CREATE INDEX idx_chat_message_sender ON chat_message(sender);

-- Audit: Track when messages were created
CREATE INDEX idx_chat_message_created_at_range ON chat_message(created_at) WHERE created_at >= CURRENT_DATE - INTERVAL '7 days';

-- Grant permissions for application user
ALTER TABLE conversation OWNER TO superchat;
ALTER TABLE chat_message OWNER TO superchat;
