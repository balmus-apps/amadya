-- QR ordering (ADR 0010): guests scan the table's QR, join its session, order and pay from their phone.

-- Restaurant setting: guest rounds and guest-started tables not answered by a waiter are confirmed after this long.
ALTER TABLE restaurant_settings ADD COLUMN guest_confirm_minutes INT NOT NULL DEFAULT 5 CHECK (guest_confirm_minutes BETWEEN 1 AND 60);

-- The code printed in each table's QR: random, never the table number, regenerated when a photo of it leaks.
-- The default is evaluated per row, so existing tables (and any inserted without a code) get their own random one.
ALTER TABLE dining_table ADD COLUMN qr_code TEXT NOT NULL DEFAULT replace(gen_random_uuid()::text, '-', '');
CREATE UNIQUE INDEX ux_dining_table_qr ON dining_table (qr_code);

-- Sessions started by guests wait for a waiter to confirm them (or for the deadline).
ALTER TABLE table_session ADD COLUMN origin TEXT NOT NULL DEFAULT 'WAITER' CHECK (origin IN ('WAITER', 'GUEST'));
ALTER TABLE table_session ADD COLUMN confirmed_at TIMESTAMPTZ;
ALTER TABLE table_session ADD COLUMN confirm_deadline TIMESTAMPTZ;
UPDATE table_session SET confirmed_at = opened_at;

CREATE TABLE table_guest
(
    id           UUID PRIMARY KEY,
    session_id   UUID        NOT NULL REFERENCES table_session (id),
    name         TEXT        NOT NULL,
    role         TEXT        NOT NULL CHECK (role IN ('OWNER', 'MEMBER')),
    status       TEXT        NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'REMOVED')),
    token_hash   TEXT        NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    decided_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_table_guest_token ON table_guest (token_hash);
CREATE INDEX ix_table_guest_session ON table_guest (session_id);

-- A round ordered by a guest, waiting for the waiter's OK; it becomes an order (round) once confirmed.
CREATE TABLE pending_round
(
    id          UUID PRIMARY KEY,
    session_id  UUID           NOT NULL REFERENCES table_session (id),
    guest_id    UUID           NOT NULL REFERENCES table_guest (id),
    lines       JSONB          NOT NULL,
    notes       TEXT,
    total       NUMERIC(12, 2) NOT NULL,
    status      TEXT           NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    deadline    TIMESTAMPTZ    NOT NULL,
    decided_at  TIMESTAMPTZ,
    decided_by  TEXT,
    reason      TEXT,
    order_id    UUID,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version     BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX ix_pending_round_session ON pending_round (session_id);
CREATE INDEX ix_pending_round_due ON pending_round (deadline) WHERE status = 'PENDING';

-- Online payments started by a guest at the table (who paid, for the log).
CREATE TABLE guest_payment
(
    payment_id UUID PRIMARY KEY,
    session_id UUID        NOT NULL REFERENCES table_session (id),
    guest_id   UUID        NOT NULL REFERENCES table_guest (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_guest_payment_guest ON guest_payment (guest_id);

-- What happened at the table: "Andrei joined", "Robert ordered 2 coffees", "Mircea paid for 2 ice creams".
CREATE TABLE table_activity
(
    id         UUID PRIMARY KEY,
    session_id UUID        NOT NULL REFERENCES table_session (id),
    at         TIMESTAMPTZ NOT NULL,
    kind       TEXT        NOT NULL,
    actor      TEXT,
    actor_kind TEXT        NOT NULL CHECK (actor_kind IN ('GUEST', 'WAITER', 'SYSTEM')),
    details    JSONB       NOT NULL DEFAULT '{}'
);
CREATE INDEX ix_table_activity_session ON table_activity (session_id, at);
