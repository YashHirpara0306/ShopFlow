-- Order Service schema. Owned by Flyway; Hibernate runs in validate mode.

CREATE TABLE orders (
    id            UUID          PRIMARY KEY,
    user_id       UUID          NOT NULL,
    status        VARCHAR(30)   NOT NULL,
    currency      VARCHAR(3)    NOT NULL DEFAULT 'INR',
    total_amount  NUMERIC(12, 2) NOT NULL,
    cancel_reason VARCHAR(40),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version       BIGINT        NOT NULL DEFAULT 0
);
CREATE INDEX ix_orders_user_created ON orders (user_id, created_at DESC);
CREATE INDEX ix_orders_status       ON orders (status);

CREATE TABLE order_item (
    id           UUID          PRIMARY KEY,
    order_id     UUID          NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id   UUID          NOT NULL,
    product_name VARCHAR(200)  NOT NULL,
    unit_price   NUMERIC(12, 2) NOT NULL,
    quantity     INT           NOT NULL
);
CREATE INDEX ix_order_item_order ON order_item (order_id);

CREATE TABLE order_status_history (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id       UUID         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    from_status    VARCHAR(30),
    to_status      VARCHAR(30)  NOT NULL,
    reason         VARCHAR(200),
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_osh_order ON order_status_history (order_id);

-- Client-supplied Idempotency-Key -> the order it produced. UNIQUE is the real guard.
CREATE TABLE idempotency_key (
    id           UUID         PRIMARY KEY,
    idem_key     VARCHAR(200) NOT NULL,
    user_id      UUID         NOT NULL,
    request_hash VARCHAR(64)  NOT NULL,
    order_id     UUID         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_idem_key UNIQUE (idem_key)
);

-- Kafka consumer de-duplication (populated from Phase 11).
CREATE TABLE processed_event (
    event_id     UUID        PRIMARY KEY,
    consumer     VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
