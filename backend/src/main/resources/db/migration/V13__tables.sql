-- tables module: floor areas, dining tables and table sessions (guests seated at a table until the bill is paid).

CREATE TABLE floor_area
(
    id         UUID PRIMARY KEY,
    name       JSONB       NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    active     BOOLEAN     NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE dining_table
(
    id         UUID PRIMARY KEY,
    area_id    UUID        NOT NULL REFERENCES floor_area (id),
    label      TEXT        NOT NULL,
    seats      INT         NOT NULL CHECK (seats > 0),
    sort_order INT         NOT NULL DEFAULT 0,
    active     BOOLEAN     NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_dining_table_label ON dining_table (lower(label));

CREATE TABLE table_session
(
    id                UUID PRIMARY KEY,
    table_id          UUID        NOT NULL REFERENCES dining_table (id),
    status            TEXT        NOT NULL CHECK (status IN ('OPEN', 'CLOSED', 'MERGED')),
    guests            INT         NOT NULL CHECK (guests > 0),
    waiter_id         UUID,
    waiter_name       TEXT,
    opened_at         TIMESTAMPTZ NOT NULL,
    closed_at         TIMESTAMPTZ,
    merged_into_id    UUID REFERENCES table_session (id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT      NOT NULL DEFAULT 0
);
-- At most one party per table at a time.
CREATE UNIQUE INDEX ux_table_session_open ON table_session (table_id) WHERE status = 'OPEN';
CREATE INDEX ix_table_session_status ON table_session (status);

CREATE INDEX ix_order_table_session ON customer_order (table_session_id) WHERE table_session_id IS NOT NULL;
