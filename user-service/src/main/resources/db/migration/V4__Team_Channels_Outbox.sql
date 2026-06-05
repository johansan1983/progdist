-- Team channels: reliable room/membership event publishing via a transactional outbox
-- (mirrors chat-service V7 outbox_event), plus channel attribution and soft-archive.

CREATE TABLE outbox_event (
    id            BIGSERIAL PRIMARY KEY,
    aggregate_id  VARCHAR(64),
    exchange      VARCHAR(120) NOT NULL,
    routing_key   VARCHAR(120) NOT NULL,
    payload       TEXT NOT NULL,
    published     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ
);

-- The relay polls unpublished rows oldest-first; partial index keeps that scan cheap.
CREATE INDEX idx_outbox_unpublished ON outbox_event (id) WHERE published = FALSE;

-- Channel attribution + soft archive
ALTER TABLE rooms ADD COLUMN created_by VARCHAR(255);
ALTER TABLE rooms ADD COLUMN archived   BOOLEAN NOT NULL DEFAULT FALSE;
