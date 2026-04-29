-- V2__Add_Performance_Indices.sql
-- Create chat tables in the shared database if auth-service already consumed version 1.

CREATE TABLE IF NOT EXISTS conversation (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_message (
  id BIGSERIAL PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  sender VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_chat_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversation(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_conversation_created_at ON conversation(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_message_conversation_created ON chat_message(conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_message_created_at ON chat_message(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_message_conversation_id ON chat_message(conversation_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_sender ON chat_message(sender);
