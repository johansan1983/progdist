-- Allow content-less messages (attachment-only)
ALTER TABLE chat_message ALTER COLUMN content DROP NOT NULL;

-- Attachment support
ALTER TABLE chat_message ADD COLUMN attachment_url  VARCHAR(512);
ALTER TABLE chat_message ADD COLUMN attachment_type VARCHAR(20);

-- View-once support
ALTER TABLE chat_message ADD COLUMN view_once BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chat_message ADD COLUMN viewed    BOOLEAN NOT NULL DEFAULT FALSE;

-- Direct messaging support
ALTER TABLE conversation ADD COLUMN type                 VARCHAR(10) NOT NULL DEFAULT 'GROUP';
ALTER TABLE conversation ADD COLUMN dm_participant_a      VARCHAR(36);
ALTER TABLE conversation ADD COLUMN dm_participant_b      VARCHAR(36);
ALTER TABLE conversation ADD COLUMN dm_participant_a_name VARCHAR(120);
ALTER TABLE conversation ADD COLUMN dm_participant_b_name VARCHAR(120);
