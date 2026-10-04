-- catalog module: categories, products, modifier groups and options.
-- vat_rate_id and station_id reference the settings module by id only (no cross-module FK, ADR 0001).

CREATE TABLE category
(
    id         UUID PRIMARY KEY,
    name       JSONB       NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    active     BOOLEAN     NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE product
(
    id            UUID PRIMARY KEY,
    category_id   UUID           NOT NULL REFERENCES category (id),
    name          JSONB          NOT NULL,
    description   JSONB,
    image_url     TEXT,
    price         NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    vat_rate_id   UUID           NOT NULL,
    station_id    UUID,
    kind          TEXT           NOT NULL DEFAULT 'RECIPE' CHECK (kind IN ('RECIPE', 'RESALE', 'SERVICE')),
    prep_time_sec INT            NOT NULL DEFAULT 300 CHECK (prep_time_sec >= 0),
    available     BOOLEAN        NOT NULL DEFAULT true,
    archived      BOOLEAN        NOT NULL DEFAULT false,
    sort_order    INT            NOT NULL DEFAULT 0,
    allergens     TEXT[]         NOT NULL DEFAULT '{}',
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version       BIGINT         NOT NULL DEFAULT 0
);
CREATE INDEX ix_product_category ON product (category_id);

CREATE TABLE modifier_group
(
    id         UUID PRIMARY KEY,
    name       JSONB       NOT NULL,
    min_select INT         NOT NULL DEFAULT 0 CHECK (min_select >= 0),
    max_select INT         NOT NULL DEFAULT 1 CHECK (max_select >= 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT      NOT NULL DEFAULT 0,
    CHECK (min_select <= max_select)
);

CREATE TABLE modifier_option
(
    id          UUID PRIMARY KEY,
    group_id    UUID           NOT NULL REFERENCES modifier_group (id) ON DELETE CASCADE,
    name        JSONB          NOT NULL,
    price_delta NUMERIC(12, 2) NOT NULL DEFAULT 0,
    available   BOOLEAN        NOT NULL DEFAULT true,
    sort_order  INT            NOT NULL DEFAULT 0
);
CREATE INDEX ix_modifier_option_group ON modifier_option (group_id);

CREATE TABLE product_modifier_group
(
    product_id UUID NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    group_id   UUID NOT NULL REFERENCES modifier_group (id),
    sort_order INT  NOT NULL DEFAULT 0, -- 0-based position (JPA @OrderColumn)
    PRIMARY KEY (product_id, group_id)
);
