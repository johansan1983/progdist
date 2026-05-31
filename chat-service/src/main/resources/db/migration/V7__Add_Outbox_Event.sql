-- Transactional outbox: events are written in the SAME transaction as the message insert,
-- then a relay publishes them to RabbitMQ with at-least-once delivery. This removes the
-- dual-write inconsistency where a message could commit but its event be lost.
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
