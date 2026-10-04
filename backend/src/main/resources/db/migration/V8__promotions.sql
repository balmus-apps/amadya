-- catalog module: promotional banners shown at the top of the customer menu.

CREATE TABLE promotion
(
    id         UUID PRIMARY KEY,
    title      JSONB       NOT NULL,
    subtitle   JSONB,
    badge      TEXT,
    image_url  TEXT,
    product_id UUID REFERENCES product (id) ON DELETE SET NULL,
    starts_at  TIMESTAMPTZ,
    ends_at    TIMESTAMPTZ,
    sort_order INT         NOT NULL DEFAULT 0,
    active     BOOLEAN     NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    BIGINT      NOT NULL DEFAULT 0,
    CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);
