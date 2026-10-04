-- identity module: users and refresh tokens.

CREATE TABLE app_user
(
    id            UUID PRIMARY KEY,
    email         TEXT        NOT NULL,
    name          TEXT        NOT NULL,
    phone         TEXT,
    password_hash TEXT        NOT NULL,
    roles         TEXT[]      NOT NULL,
    locale        TEXT        NOT NULL DEFAULT 'ro' CHECK (locale IN ('ro', 'en')),
    active        BOOLEAN     NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_app_user_email ON app_user (lower(email));

CREATE TABLE refresh_token
(
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash  TEXT        NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);
