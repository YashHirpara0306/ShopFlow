-- User Service schema. Owned by Flyway; Hibernate runs in validate mode.

CREATE TABLE app_user (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    phone         VARCHAR(20),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uq_app_user_email UNIQUE (email)
);

-- Roles as a small child table (a user has 1..n). Kept simple: an enum name per row.
CREATE TABLE user_role (
    user_id UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role    VARCHAR(30) NOT NULL,
    PRIMARY KEY (user_id, role)
);

-- Opaque refresh tokens. Only the SHA-256 hash of the token is stored, never the token itself.
-- family_id groups a rotation chain so a detected reuse can revoke the whole chain.
CREATE TABLE refresh_token (
    id             UUID        PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash     VARCHAR(64) NOT NULL,
    family_id      UUID        NOT NULL,
    issued_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked        BOOLEAN     NOT NULL DEFAULT FALSE,
    replaced_by_id UUID,
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_token_user   ON refresh_token (user_id);
CREATE INDEX ix_refresh_token_family ON refresh_token (family_id);
