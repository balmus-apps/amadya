-- payments module: cash and card-terminal payments taken at the table, against a table session rather than one order.

ALTER TABLE payment ALTER COLUMN order_id DROP NOT NULL;
ALTER TABLE payment ADD COLUMN table_session_id UUID;
ALTER TABLE payment ADD COLUMN tendered NUMERIC(12, 2);
ALTER TABLE payment ADD COLUMN recorded_by UUID;
ALTER TABLE payment ADD COLUMN voided_at TIMESTAMPTZ;
ALTER TABLE payment ADD CONSTRAINT ck_payment_target CHECK (order_id IS NOT NULL OR table_session_id IS NOT NULL);
ALTER TABLE payment DROP CONSTRAINT payment_status_check;
ALTER TABLE payment ADD CONSTRAINT payment_status_check CHECK (status IN ('PENDING', 'CAPTURED', 'FAILED', 'REFUNDED', 'VOIDED'));
CREATE INDEX ix_payment_table_session ON payment (table_session_id) WHERE table_session_id IS NOT NULL;

-- Which units a payment covered when the bill was split item by item.
CREATE TABLE payment_allocation
(
    id            UUID PRIMARY KEY,
    payment_id    UUID           NOT NULL REFERENCES payment (id) ON DELETE CASCADE,
    order_line_id UUID           NOT NULL,
    quantity      INT            NOT NULL CHECK (quantity > 0),
    amount        NUMERIC(12, 2) NOT NULL
);
CREATE INDEX ix_payment_allocation_payment ON payment_allocation (payment_id);
