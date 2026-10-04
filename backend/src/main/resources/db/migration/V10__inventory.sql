-- inventory module: units of measure, warehouses, stock items, FIFO lots, append-only ledger,
-- balances (projection), deficits, recipes and stock documents. See docs/domain/warehouses.md and units-of-measure.md.

CREATE TABLE unit_of_measure
(
    id           UUID PRIMARY KEY,
    code         TEXT          NOT NULL,
    name         JSONB         NOT NULL,
    dimension    TEXT CHECK (dimension IN ('MASS', 'VOLUME', 'COUNT', 'LENGTH')),
    factor       NUMERIC(18, 6) CHECK (factor > 0),
    is_reference BOOLEAN       NOT NULL DEFAULT false,
    aliases      TEXT[]        NOT NULL DEFAULT '{}',
    status       TEXT          NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'UNMAPPED')),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version      BIGINT        NOT NULL DEFAULT 0,
    CHECK (NOT is_reference OR factor = 1),
    CHECK (status = 'UNMAPPED' OR (dimension IS NOT NULL AND factor IS NOT NULL))
);
CREATE UNIQUE INDEX ux_uom_code ON unit_of_measure (lower(code));
CREATE UNIQUE INDEX ux_uom_reference ON unit_of_measure (dimension) WHERE is_reference;

-- Reference units (factor 1) and standard units. Aliases include UN/ECE Rec 20 codes used by e-Factura.
INSERT INTO unit_of_measure (id, code, name, dimension, factor, is_reference, aliases)
VALUES ('0190a000-0000-7000-8000-000000001001', 'g', '{"ro":"gram","en":"gram"}', 'MASS', 1, true, '{gr,gram,grame,GRM}'),
       ('0190a000-0000-7000-8000-000000001002', 'kg', '{"ro":"kilogram","en":"kilogram"}', 'MASS', 1000, false, '{kgr,kilogram,kilograme,KGM}'),
       ('0190a000-0000-7000-8000-000000001003', 'mg', '{"ro":"miligram","en":"milligram"}', 'MASS', 0.001, false, '{MGM}'),
       ('0190a000-0000-7000-8000-000000001004', 'ml', '{"ro":"mililitru","en":"millilitre"}', 'VOLUME', 1, true, '{MLT}'),
       ('0190a000-0000-7000-8000-000000001005', 'l', '{"ro":"litru","en":"litre"}', 'VOLUME', 1000, false, '{ltr,litru,litri,LTR}'),
       ('0190a000-0000-7000-8000-000000001006', 'cl', '{"ro":"centilitru","en":"centilitre"}', 'VOLUME', 10, false, '{CLT}'),
       ('0190a000-0000-7000-8000-000000001007', 'buc', '{"ro":"bucată","en":"piece"}', 'COUNT', 1, true, '{pcs,pc,bucati,buc.,H87,C62,EA,XPP}'),
       ('0190a000-0000-7000-8000-000000001008', 'cm', '{"ro":"centimetru","en":"centimetre"}', 'LENGTH', 1, true, '{CMT}'),
       ('0190a000-0000-7000-8000-000000001009', 'm', '{"ro":"metru","en":"metre"}', 'LENGTH', 100, false, '{MTR}');

CREATE TABLE warehouse
(
    id         UUID PRIMARY KEY,
    code       TEXT        NOT NULL,
    name       JSONB       NOT NULL,
    type       TEXT        NOT NULL CHECK (type IN ('INGREDIENTS', 'FINISHED_GOODS', 'FIXED_ASSETS', 'CONSUMABLES')),
    active     BOOLEAN     NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_warehouse_code ON warehouse (lower(code));

INSERT INTO warehouse (id, code, name, type)
VALUES ('0190a000-0000-7000-8000-000000001101', 'MATERII', '{"ro":"Materii prime","en":"Ingredients"}', 'INGREDIENTS'),
       ('0190a000-0000-7000-8000-000000001102', 'MARFA', '{"ro":"Mărfuri","en":"Finished goods"}', 'FINISHED_GOODS'),
       ('0190a000-0000-7000-8000-000000001103', 'OBIECTE', '{"ro":"Obiecte de inventar","en":"Fixed assets"}', 'FIXED_ASSETS'),
       ('0190a000-0000-7000-8000-000000001104', 'CONSUMABILE', '{"ro":"Consumabile","en":"Consumables"}', 'CONSUMABLES');

CREATE TABLE stock_item
(
    id                   UUID PRIMARY KEY,
    sku                  TEXT           NOT NULL,
    name                 JSONB          NOT NULL,
    type                 TEXT           NOT NULL CHECK (type IN ('INGREDIENTS', 'FINISHED_GOODS', 'FIXED_ASSETS', 'CONSUMABLES')),
    base_unit_id         UUID           NOT NULL REFERENCES unit_of_measure (id),
    display_unit_id      UUID REFERENCES unit_of_measure (id),
    default_warehouse_id UUID REFERENCES warehouse (id),
    min_stock            NUMERIC(14, 3) NOT NULL DEFAULT 0,
    active               BOOLEAN        NOT NULL DEFAULT true,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version              BIGINT         NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_stock_item_sku ON stock_item (lower(sku));

CREATE TABLE stock_item_packaging
(
    id            UUID PRIMARY KEY,
    stock_item_id UUID           NOT NULL REFERENCES stock_item (id) ON DELETE CASCADE,
    name          TEXT           NOT NULL,
    qty_in_base   NUMERIC(14, 3) NOT NULL CHECK (qty_in_base > 0),
    barcode       TEXT
);
CREATE UNIQUE INDEX ux_packaging_name ON stock_item_packaging (stock_item_id, lower(name));

-- FIFO lots: one per receipt; consumed oldest first (received_at, seq).
CREATE TABLE stock_lot
(
    id            UUID PRIMARY KEY,
    seq           BIGSERIAL      NOT NULL,
    warehouse_id  UUID           NOT NULL REFERENCES warehouse (id),
    stock_item_id UUID           NOT NULL REFERENCES stock_item (id),
    received_at   TIMESTAMPTZ    NOT NULL,
    qty_initial   NUMERIC(14, 3) NOT NULL CHECK (qty_initial > 0),
    qty_remaining NUMERIC(14, 3) NOT NULL CHECK (qty_remaining >= 0),
    unit_cost     NUMERIC(18, 6) NOT NULL CHECK (unit_cost >= 0),
    movement_id   UUID           NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_stock_lot_open ON stock_lot (warehouse_id, stock_item_id, received_at, seq) WHERE qty_remaining > 0;

-- Append-only ledger; quantity is signed and in the item's base unit.
CREATE TABLE stock_movement
(
    id            UUID PRIMARY KEY,
    warehouse_id  UUID           NOT NULL REFERENCES warehouse (id),
    stock_item_id UUID           NOT NULL REFERENCES stock_item (id),
    type          TEXT           NOT NULL,
    quantity      NUMERIC(14, 3) NOT NULL,
    total_cost    NUMERIC(16, 4) NOT NULL,
    source_type   TEXT           NOT NULL,
    source_id     UUID,
    source_ref    TEXT,
    user_id       UUID,
    occurred_at   TIMESTAMPTZ    NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_stock_movement_item ON stock_movement (stock_item_id, occurred_at DESC);
CREATE INDEX ix_stock_movement_source ON stock_movement (source_type, source_id);

CREATE TABLE stock_lot_allocation
(
    id          UUID PRIMARY KEY,
    movement_id UUID           NOT NULL REFERENCES stock_movement (id),
    lot_id      UUID           NOT NULL REFERENCES stock_lot (id),
    quantity    NUMERIC(14, 3) NOT NULL CHECK (quantity > 0),
    unit_cost   NUMERIC(18, 6) NOT NULL
);
CREATE INDEX ix_allocation_movement ON stock_lot_allocation (movement_id);

-- Quantity issued without stock (kitchens keep serving); settled FIFO against the next receipt.
CREATE TABLE stock_deficit
(
    id            UUID PRIMARY KEY,
    warehouse_id  UUID           NOT NULL REFERENCES warehouse (id),
    stock_item_id UUID           NOT NULL REFERENCES stock_item (id),
    movement_id   UUID           NOT NULL REFERENCES stock_movement (id),
    qty_open      NUMERIC(14, 3) NOT NULL CHECK (qty_open >= 0),
    estimated_unit_cost NUMERIC(18, 6) NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX ix_deficit_open ON stock_deficit (warehouse_id, stock_item_id, created_at) WHERE qty_open > 0;

-- Projection of lots + deficits, for fast listing.
CREATE TABLE stock_balance
(
    warehouse_id  UUID           NOT NULL REFERENCES warehouse (id),
    stock_item_id UUID           NOT NULL REFERENCES stock_item (id),
    quantity      NUMERIC(14, 3) NOT NULL DEFAULT 0,
    value         NUMERIC(16, 4) NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (warehouse_id, stock_item_id)
);

-- Ingredients per sold product or modifier option (product/option ids belong to the catalog module).
CREATE TABLE recipe_line
(
    id            UUID PRIMARY KEY,
    product_id    UUID,
    option_id     UUID,
    stock_item_id UUID           NOT NULL REFERENCES stock_item (id),
    quantity      NUMERIC(14, 3) NOT NULL CHECK (quantity > 0),
    warehouse_id  UUID REFERENCES warehouse (id),
    position      INT            NOT NULL DEFAULT 0,
    CHECK ((product_id IS NULL) <> (option_id IS NULL))
);
CREATE INDEX ix_recipe_product ON recipe_line (product_id);
CREATE INDEX ix_recipe_option ON recipe_line (option_id);

-- Transfers, consumption notes (bon de consum), waste and inventory counts.
CREATE TABLE stock_document
(
    id                  UUID PRIMARY KEY,
    number              TEXT        NOT NULL UNIQUE,
    type                TEXT        NOT NULL CHECK (type IN ('TRANSFER', 'CONSUMPTION', 'WASTE', 'COUNT')),
    doc_date            DATE        NOT NULL,
    warehouse_id        UUID        NOT NULL REFERENCES warehouse (id),
    target_warehouse_id UUID REFERENCES warehouse (id),
    note                TEXT,
    total_cost          NUMERIC(16, 4) NOT NULL DEFAULT 0,
    user_id             UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE stock_document_line
(
    id              UUID PRIMARY KEY,
    document_id     UUID           NOT NULL REFERENCES stock_document (id) ON DELETE CASCADE,
    position        INT            NOT NULL,
    stock_item_id   UUID           NOT NULL REFERENCES stock_item (id),
    quantity        NUMERIC(14, 3) NOT NULL,
    system_quantity NUMERIC(14, 3),
    cost            NUMERIC(16, 4) NOT NULL DEFAULT 0,
    reason          TEXT
);

-- Yearly numbering for stock documents and NIRs (TR-2026-000001, NIR-2026-000001, ...).
CREATE TABLE document_counter
(
    prefix     TEXT NOT NULL,
    year       INT  NOT NULL,
    last_value INT  NOT NULL,
    PRIMARY KEY (prefix, year)
);
