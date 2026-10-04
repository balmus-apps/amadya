-- notifications module: Expo push tokens registered per order by the customer app.
CREATE TABLE push_subscription
(
    id         UUID PRIMARY KEY,
    order_id   UUID        NOT NULL,
    expo_token TEXT        NOT NULL,
    locale     TEXT        NOT NULL DEFAULT 'ro',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (order_id, expo_token)
);
