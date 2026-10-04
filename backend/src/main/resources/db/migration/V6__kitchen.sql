-- kitchen module: one ticket per order and station.

CREATE TABLE kitchen_ticket
(
    id                 UUID PRIMARY KEY,
    order_id           UUID        NOT NULL,
    order_number       TEXT        NOT NULL,
    channel            TEXT        NOT NULL,
    station_id         UUID        NOT NULL,
    status             TEXT        NOT NULL CHECK (status IN ('QUEUED', 'IN_PROGRESS', 'READY')),
    prep_time_sec      INT         NOT NULL,
    queued_at          TIMESTAMPTZ NOT NULL,
    started_at         TIMESTAMPTZ,
    ready_at           TIMESTAMPTZ,
    estimated_ready_at TIMESTAMPTZ NOT NULL,
    notes              TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_kitchen_ticket_order_station ON kitchen_ticket (order_id, station_id);
CREATE INDEX ix_kitchen_ticket_station_status ON kitchen_ticket (station_id, status, queued_at);

CREATE TABLE kitchen_ticket_line
(
    id           UUID PRIMARY KEY,
    ticket_id    UUID   NOT NULL REFERENCES kitchen_ticket (id) ON DELETE CASCADE,
    position     INT    NOT NULL,
    product_name JSONB  NOT NULL,
    quantity     INT    NOT NULL,
    modifiers    JSONB  NOT NULL DEFAULT '[]',
    notes        TEXT
);
CREATE INDEX ix_kitchen_ticket_line_ticket ON kitchen_ticket_line (ticket_id);
