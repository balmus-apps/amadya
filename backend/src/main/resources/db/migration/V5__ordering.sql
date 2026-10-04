-- ordering module: orders, lines with modifier snapshots, daily order number counter.

CREATE TABLE customer_order
(
    id                  UUID PRIMARY KEY,
    number              TEXT           NOT NULL,
    business_date       DATE           NOT NULL,
    channel             TEXT           NOT NULL CHECK (channel IN ('TAKEAWAY', 'DINE_IN', 'COUNTER')),
    status              TEXT           NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'PLACED', 'PREPARING', 'READY', 'COMPLETED', 'CANCELLED')),
    payment_status      TEXT           NOT NULL DEFAULT 'UNPAID' CHECK (payment_status IN ('UNPAID', 'PAID', 'REFUNDED')),
    customer_name       TEXT,
    customer_phone      TEXT,
    customer_email      TEXT,
    customer_user_id    UUID,
    created_by_user_id  UUID,
    table_session_id    UUID,
    pickup_at           TIMESTAMPTZ,
    estimated_ready_at  TIMESTAMPTZ,
    placed_at           TIMESTAMPTZ,
    ready_at            TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    cancel_reason       TEXT,
    notes               TEXT,
    locale              TEXT           NOT NULL DEFAULT 'ro',
    currency            TEXT            NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    total               NUMERIC(12, 2) NOT NULL,
    vat_total           NUMERIC(12, 2) NOT NULL,
    tracking_token_hash TEXT,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version             BIGINT         NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_order_number_day ON customer_order (business_date, number);
CREATE INDEX ix_order_status ON customer_order (status, created_at);

CREATE TABLE order_line
(
    id           UUID PRIMARY KEY,
    order_id     UUID           NOT NULL REFERENCES customer_order (id) ON DELETE CASCADE,
    position     INT            NOT NULL,
    product_id   UUID           NOT NULL,
    product_name JSONB          NOT NULL,
    station_id   UUID,
    prep_time_sec INT           NOT NULL DEFAULT 0,
    quantity     INT            NOT NULL CHECK (quantity > 0),
    unit_price   NUMERIC(12, 2) NOT NULL,
    vat_percent  NUMERIC(5, 2)  NOT NULL,
    total        NUMERIC(12, 2) NOT NULL,
    vat_amount   NUMERIC(12, 2) NOT NULL,
    notes        TEXT
);
CREATE INDEX ix_order_line_order ON order_line (order_id);

CREATE TABLE order_line_modifier
(
    id          UUID PRIMARY KEY,
    line_id     UUID           NOT NULL REFERENCES order_line (id) ON DELETE CASCADE,
    option_id   UUID           NOT NULL,
    name        JSONB          NOT NULL,
    price_delta NUMERIC(12, 2) NOT NULL
);
CREATE INDEX ix_order_line_modifier_line ON order_line_modifier (line_id);

CREATE TABLE order_number_counter
(
    business_date DATE PRIMARY KEY,
    last_value    INT NOT NULL
);
