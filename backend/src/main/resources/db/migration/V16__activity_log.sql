-- audit module: the activity log, one row per write action taken by staff (any app). Append-only: rows are only
-- removed by the retention job, after the number of days set by the admin.

ALTER TABLE restaurant_settings ADD COLUMN activity_retention_days INT NOT NULL DEFAULT 30 CHECK (activity_retention_days BETWEEN 1 AND 3650);

CREATE TABLE activity_log
(
    id          UUID PRIMARY KEY,
    at          TIMESTAMPTZ NOT NULL,
    actor_id    UUID,
    actor_name  TEXT        NOT NULL,
    actor_roles TEXT[]      NOT NULL,
    app         TEXT        NOT NULL,
    action      TEXT        NOT NULL,
    method      TEXT        NOT NULL,
    path        TEXT        NOT NULL,
    status      INT         NOT NULL,
    target_id   TEXT,
    target      TEXT,
    detail      TEXT,
    error       TEXT,
    ip          TEXT
);
CREATE INDEX ix_activity_log_at ON activity_log (at);
CREATE INDEX ix_activity_log_actor ON activity_log (actor_id, id);
