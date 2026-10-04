-- payments module: one row per payment attempt with a provider.

CREATE TABLE payment
(
    id           UUID PRIMARY KEY,
    order_id     UUID           NOT NULL,
    method       TEXT           NOT NULL CHECK (method IN ('CASH', 'CARD_POS', 'ONLINE')),
    provider     TEXT           NOT NULL,
    provider_ref TEXT,
    amount       NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency     TEXT            NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status       TEXT           NOT NULL CHECK (status IN ('PENDING', 'CAPTURED', 'FAILED', 'REFUNDED')),
    captured_at  TIMESTAMPTZ,
    refunded_at  TIMESTAMPTZ,
    failure      TEXT,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version      BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX ix_payment_order ON payment (order_id);
CREATE UNIQUE INDEX ux_payment_provider_ref ON payment (provider, provider_ref) WHERE provider_ref IS NOT NULL;
