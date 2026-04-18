CREATE TABLE IF NOT EXISTS events (
    id          BIGSERIAL PRIMARY KEY,
    event_type  VARCHAR(50)  NOT NULL,
    user_id     VARCHAR(100) NOT NULL,
    session_id  VARCHAR(100) NOT NULL,
    timestamp   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS page_view_events (
    event_id  BIGINT PRIMARY KEY REFERENCES events (id) ON DELETE CASCADE,
    page_url  VARCHAR(500) NOT NULL,
    referrer  VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS purchase_events (
    event_id    BIGINT PRIMARY KEY REFERENCES events (id) ON DELETE CASCADE,
    product_id  VARCHAR(100)  NOT NULL,
    amount      NUMERIC(12, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS error_events (
    event_id      BIGINT PRIMARY KEY REFERENCES events (id) ON DELETE CASCADE,
    error_code    VARCHAR(50) NOT NULL,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_events_event_type ON events (event_type);
CREATE INDEX IF NOT EXISTS idx_events_user_id    ON events (user_id);
CREATE INDEX IF NOT EXISTS idx_events_timestamp  ON events (timestamp);