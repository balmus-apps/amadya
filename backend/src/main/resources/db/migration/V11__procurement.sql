-- procurement module: suppliers, purchase invoices (manual / e-Factura XML / scan), NIR.
-- Stock items, units and warehouses belong to the inventory module and are referenced by id only.

CREATE TABLE supplier
(
    id         UUID PRIMARY KEY,
    name       TEXT        NOT NULL,
    cui        TEXT,
    reg_com    TEXT,
    address    TEXT,
    iban       TEXT,
    email      TEXT,
    phone      TEXT,
    active     BOOLEAN     NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_supplier_cui ON supplier (cui) WHERE cui IS NOT NULL;

-- What a supplier's code / description means in our stock (learned when lines are matched).
CREATE TABLE supplier_item
(
    id            UUID PRIMARY KEY,
    supplier_id   UUID NOT NULL REFERENCES supplier (id) ON DELETE CASCADE,
    supplier_code TEXT,
    description   TEXT NOT NULL,
    stock_item_id UUID NOT NULL,
    unit_id       UUID,
    packaging_id  UUID,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_supplier_item_code ON supplier_item (supplier_id, supplier_code) WHERE supplier_code IS NOT NULL;
CREATE INDEX ix_supplier_item_description ON supplier_item (supplier_id, lower(description));

CREATE TABLE purchase_invoice
(
    id          UUID PRIMARY KEY,
    supplier_id UUID           NOT NULL REFERENCES supplier (id),
    series      TEXT,
    number      TEXT           NOT NULL,
    issue_date  DATE           NOT NULL,
    due_date    DATE,
    currency    TEXT           NOT NULL DEFAULT 'RON',
    total_net   NUMERIC(14, 2) NOT NULL,
    total_vat   NUMERIC(14, 2) NOT NULL,
    total_gross NUMERIC(14, 2) NOT NULL,
    source      TEXT           NOT NULL CHECK (source IN ('MANUAL', 'EFACTURA_XML', 'SCAN')),
    raw         TEXT,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version     BIGINT         NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_invoice_number ON purchase_invoice (supplier_id, coalesce(series, ''), number);

CREATE TABLE purchase_invoice_line
(
    id            UUID PRIMARY KEY,
    invoice_id    UUID           NOT NULL REFERENCES purchase_invoice (id) ON DELETE CASCADE,
    position      INT            NOT NULL,
    supplier_code TEXT,
    description   TEXT           NOT NULL,
    quantity      NUMERIC(14, 3) NOT NULL,
    unit_code     TEXT,
    unit_id       UUID,
    packaging_id  UUID,
    unit_price    NUMERIC(18, 6) NOT NULL,
    vat_percent   NUMERIC(5, 2)  NOT NULL,
    line_net      NUMERIC(14, 2) NOT NULL,
    stock_item_id UUID
);

CREATE TABLE nir
(
    id                UUID PRIMARY KEY,
    number            TEXT UNIQUE,
    status            TEXT        NOT NULL CHECK (status IN ('DRAFT', 'POSTED', 'REVERSED')),
    nir_date          DATE        NOT NULL,
    warehouse_id      UUID        NOT NULL,
    supplier_id       UUID REFERENCES supplier (id),
    invoice_id        UUID REFERENCES purchase_invoice (id),
    invoice_ref       TEXT,
    delivery_note_ref TEXT,
    committee         TEXT[]      NOT NULL DEFAULT '{}',
    notes             TEXT,
    reversal_of_id    UUID REFERENCES nir (id),
    posted_at         TIMESTAMPTZ,
    posted_by         UUID,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version           BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_nir_invoice ON nir (invoice_id) WHERE invoice_id IS NOT NULL AND reversal_of_id IS NULL;

CREATE TABLE nir_line
(
    id                 UUID PRIMARY KEY,
    nir_id             UUID           NOT NULL REFERENCES nir (id) ON DELETE CASCADE,
    position           INT            NOT NULL,
    stock_item_id      UUID           NOT NULL,
    unit_id            UUID,
    packaging_id       UUID,
    qty_document       NUMERIC(14, 3) NOT NULL,
    qty_received       NUMERIC(14, 3) NOT NULL,
    qty_difference     NUMERIC(14, 3) GENERATED ALWAYS AS (qty_received - qty_document) STORED,
    unit_price         NUMERIC(18, 6) NOT NULL CHECK (unit_price >= 0),
    vat_percent        NUMERIC(5, 2)  NOT NULL DEFAULT 0,
    discrepancy_reason TEXT
);
