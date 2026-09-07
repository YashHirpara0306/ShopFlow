-- Transactional outbox (ADR-0005 #2). Order Service writes a domain change and an outbox row in
-- one local transaction; OutboxRelay publishes unpublished rows to Kafka. This removes the
-- "DB committed but event not published" dual-write gap on the order write path.

CREATE TABLE outbox_event (
    id            UUID         PRIMARY KEY,
    topic         VARCHAR(60)  NOT NULL,
    partition_key VARCHAR(80)  NOT NULL,
    payload       TEXT         NOT NULL,       -- the full EventEnvelope JSON
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ
);
CREATE INDEX ix_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
