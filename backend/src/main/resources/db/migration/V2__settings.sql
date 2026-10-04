-- settings module: single-row restaurant settings, VAT rates, preparation stations.

CREATE TABLE restaurant_settings
(
    id                  SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    name                TEXT        NOT NULL,
    legal_name          TEXT,
    cui                 TEXT,
    reg_com             TEXT,
    address             TEXT,
    phone               TEXT,
    email               TEXT,
    logo_url            TEXT,
    default_locale      TEXT        NOT NULL DEFAULT 'ro' CHECK (default_locale IN ('ro', 'en')),
    locales             TEXT[]      NOT NULL DEFAULT '{ro,en}',
    currency            TEXT         NOT NULL DEFAULT 'RON' CHECK (currency ~ '^[A-Z]{3}$'),
    theme               JSONB       NOT NULL DEFAULT '{}',
    features            JSONB       NOT NULL,
    opening_hours       JSONB       NOT NULL DEFAULT '[]',
    order_number_prefix TEXT        NOT NULL DEFAULT 'A',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT      NOT NULL DEFAULT 0
);

INSERT INTO restaurant_settings (name, theme, features)
VALUES ('Amadya',
        '{"primary":"#B3261E","secondary":"#2E7D32","background":"#FFF8F0","foreground":"#1F1A17","radius":"12px"}',
        '{"takeaway":true,"tables":true,"onlinePayments":true,"kitchenDisplay":true,"queueDisplay":true,"invoiceOcr":false}');

CREATE TABLE vat_rate
(
    id           UUID PRIMARY KEY,
    code         TEXT          NOT NULL,
    name         JSONB         NOT NULL,
    percent      NUMERIC(5, 2) NOT NULL CHECK (percent >= 0 AND percent < 100),
    fiscal_group TEXT          NOT NULL,
    active       BOOLEAN       NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version      BIGINT        NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_vat_rate_code ON vat_rate (lower(code));

-- Romanian VAT rates in force since 2025-08-01. Fiscal groups follow the common AMEF layout; adjust per printer.
INSERT INTO vat_rate (id, code, name, percent, fiscal_group)
VALUES ('0190a000-0000-7000-8000-000000000001', 'STD', '{"ro":"Cota standard 21%","en":"Standard rate 21%"}', 21.00, 'A'),
       ('0190a000-0000-7000-8000-000000000002', 'RED', '{"ro":"Cota redusă 11%","en":"Reduced rate 11%"}', 11.00, 'B'),
       ('0190a000-0000-7000-8000-000000000003', 'ZERO', '{"ro":"Scutit","en":"Exempt"}', 0.00, 'E');

CREATE TABLE station
(
    id             UUID PRIMARY KEY,
    code           TEXT        NOT NULL,
    name           JSONB       NOT NULL,
    parallel_slots INT         NOT NULL DEFAULT 2 CHECK (parallel_slots > 0),
    active         BOOLEAN     NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_station_code ON station (lower(code));

INSERT INTO station (id, code, name, parallel_slots)
VALUES ('0190a000-0000-7000-8000-000000000101', 'KITCHEN', '{"ro":"Bucătărie","en":"Kitchen"}', 2);
