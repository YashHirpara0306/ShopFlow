-- Payment Service schema. Owned by Flyway; Hibernate runs in validate mode.

-- One payment per order. order_id UNIQUE is the idempotency guard against double charging.
CREATE TABLE payment (
    id             UUID          PRIMARY KEY,
    order_id       UUID          NOT NULL,
    amount         NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency       VARCHAR(3)    NOT NULL DEFAULT 'INR',
    status         VARCHAR(20)   NOT NULL,          -- PENDING | CAPTURED | FAILED
    provider_ref   VARCHAR(64),
    failure_reason VARCHAR(40),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_order UNIQUE (order_id)
);
CREATE INDEX ix_payment_status ON payment (status);

CREATE TABLE refund (
    id         UUID          PRIMARY KEY,
    payment_id UUID          NOT NULL REFERENCES payment (id),
    order_id   UUID          NOT NULL,
    amount     NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    status     VARCHAR(20)   NOT NULL,              -- COMPLETED
    reason     VARCHAR(200),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_refund_order UNIQUE (order_id)
);

CREATE TABLE processed_event (
    event_id     UUID        PRIMARY KEY,
    consumer     VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
