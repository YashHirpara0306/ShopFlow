-- Notification Service schema. Owned by Flyway; Hibernate runs in validate mode.

CREATE TABLE notification (
    id         UUID          PRIMARY KEY,
    event_id   UUID          NOT NULL,
    user_id    UUID          NOT NULL,
    channel    VARCHAR(10)   NOT NULL,           -- EMAIL | SMS
    template   VARCHAR(60)   NOT NULL,
    data       VARCHAR(2000),
    status     VARCHAR(10)   NOT NULL,           -- PENDING | SENT | FAILED
    attempts   INT           NOT NULL DEFAULT 0,
    last_error VARCHAR(300),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    sent_at    TIMESTAMPTZ,
    -- one message per (source event, channel): a redelivered event does not resend
    CONSTRAINT uq_notification_event_channel UNIQUE (event_id, channel)
);
CREATE INDEX ix_notification_user   ON notification (user_id);
CREATE INDEX ix_notification_status ON notification (status);

CREATE TABLE notification_attempt (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    notification_id UUID        NOT NULL REFERENCES notification (id) ON DELETE CASCADE,
    attempt_no      INT         NOT NULL,
    outcome         VARCHAR(10) NOT NULL,        -- SENT | FAILED
    error           VARCHAR(300),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_notification_attempt_notification ON notification_attempt (notification_id);

CREATE TABLE processed_event (
    event_id     UUID        PRIMARY KEY,
    consumer     VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
