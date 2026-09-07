-- Inventory Service schema. Owned by Flyway; Hibernate runs in validate mode.

-- One row per product. Invariant: available + reserved <= total, all >= 0.
CREATE TABLE stock (
    product_id UUID   PRIMARY KEY,
    total      BIGINT NOT NULL DEFAULT 0 CHECK (total >= 0),
    available  BIGINT NOT NULL DEFAULT 0 CHECK (available >= 0),
    reserved   BIGINT NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT NOT NULL DEFAULT 0
);

-- One reservation per order. order_id UNIQUE is the idempotency guard.
CREATE TABLE inventory_reservation (
    id         UUID        PRIMARY KEY,
    order_id   UUID        NOT NULL,
    status     VARCHAR(20) NOT NULL,           -- RESERVED | RELEASED | CONFIRMED
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_reservation_order UNIQUE (order_id)
);

CREATE TABLE reservation_item (
    id             UUID   PRIMARY KEY,
    reservation_id UUID   NOT NULL REFERENCES inventory_reservation (id) ON DELETE CASCADE,
    product_id     UUID   NOT NULL,
    quantity       BIGINT NOT NULL CHECK (quantity > 0)
);
CREATE INDEX ix_reservation_item_reservation ON reservation_item (reservation_id);

CREATE TABLE stock_adjustment_audit (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id UUID        NOT NULL,
    delta      BIGINT      NOT NULL,
    new_total  BIGINT      NOT NULL,
    actor      VARCHAR(64),
    reason     VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_stock_audit_product ON stock_adjustment_audit (product_id);

CREATE TABLE processed_event (
    event_id     UUID        PRIMARY KEY,
    consumer     VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
