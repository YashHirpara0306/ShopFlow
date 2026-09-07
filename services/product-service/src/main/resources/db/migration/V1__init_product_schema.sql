-- Product Service schema. Owned by Flyway; Hibernate runs in validate mode.

CREATE TABLE category (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_category_slug UNIQUE (slug)
);

CREATE TABLE product (
    id          UUID          PRIMARY KEY,
    sku         VARCHAR(64)   NOT NULL,
    name        VARCHAR(200)  NOT NULL,
    description VARCHAR(5000),
    price       NUMERIC(12, 2) NOT NULL,
    currency    VARCHAR(3)    NOT NULL DEFAULT 'INR',
    category_id UUID          NOT NULL REFERENCES category (id),
    image_url   VARCHAR(500),
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version     BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_product_sku UNIQUE (sku)
);

CREATE INDEX ix_product_category   ON product (category_id);
CREATE INDEX ix_product_active     ON product (active);
CREATE INDEX ix_product_name_lower ON product (lower(name));
CREATE INDEX ix_product_price      ON product (price);
