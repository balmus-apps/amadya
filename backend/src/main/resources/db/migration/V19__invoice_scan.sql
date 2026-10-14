-- invoice-scan-import: every field of a supplier document (factura / aviz / bon), keyword name rules, warehouse per line.

ALTER TABLE purchase_invoice
    ADD COLUMN doc_type        TEXT NOT NULL DEFAULT 'FACTURA' CHECK (doc_type IN ('FACTURA', 'AVIZ', 'BON')),
    ADD COLUMN issue_time      TIME,
    ADD COLUMN supplier_branch TEXT,          -- store that issued it, e.g. "Selgros Galați, B-dul Galați nr. 1-C"
    ADD COLUMN client_no       TEXT,          -- Nr. client
    ADD COLUMN card_no         TEXT,          -- Nr. legitimație
    ADD COLUMN register_no     TEXT,          -- Număr casă
    ADD COLUMN cashier_no      TEXT,          -- Număr casier
    ADD COLUMN buyer_name      TEXT,
    ADD COLUMN buyer_cui       TEXT,
    ADD COLUMN buyer_person    TEXT,          -- Cumpărător: the person who bought
    ADD COLUMN payment_method  TEXT CHECK (payment_method IN ('CASH', 'CARD', 'TRANSFER', 'OTHER')),
    ADD COLUMN paid_amount     NUMERIC(14, 2),
    ADD COLUMN discount_total  NUMERIC(14, 2),
    ADD COLUMN file_key        TEXT,          -- original photo/PDF in private media storage
    ADD COLUMN raw_extraction  JSONB;         -- what the extractor returned, untouched

ALTER TABLE purchase_invoice_line
    ADD COLUMN original_description TEXT,     -- as printed; description holds our normalised name
    ADD COLUMN lot                  TEXT,
    ADD COLUMN warehouse_id         UUID,
    ADD COLUMN line_vat             NUMERIC(14, 2),
    ADD COLUMN line_gross           NUMERIC(14, 2);

-- Keyword rules: "LAPTE|MILK" → "Lapte". Tried by priority, first match wins (case and diacritics ignored).
CREATE TABLE product_name_rule
(
    id            UUID PRIMARY KEY,
    match_type    TEXT        NOT NULL CHECK (match_type IN ('CONTAINS', 'STARTS_WITH', 'REGEX')),
    pattern       TEXT        NOT NULL,
    target_name   TEXT        NOT NULL,
    stock_item_id UUID,
    priority      INT         NOT NULL DEFAULT 100,
    active        BOOLEAN     NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    version       BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX ix_product_name_rule_order ON product_name_rule (priority, created_at) WHERE active;

-- "BU" / "BUC" (bucată) as printed by cash-and-carry receipts.
UPDATE unit_of_measure
SET aliases = aliases || ARRAY ['BU', 'BUC', 'bucata', 'bucată']
WHERE id = '0190a000-0000-7000-8000-000000001007'
  AND NOT ('BU' = ANY (aliases));

-- A document can fill several warehouses: one NIR per (invoice, warehouse) instead of one per invoice.
DROP INDEX ux_nir_invoice;
CREATE UNIQUE INDEX ux_nir_invoice_warehouse ON nir (invoice_id, warehouse_id) WHERE invoice_id IS NOT NULL AND reversal_of_id IS NULL;
