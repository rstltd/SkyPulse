CREATE TABLE hazard_alerts (
    id              BIGSERIAL PRIMARY KEY,
    alert_time      TIMESTAMPTZ NOT NULL,
    alert_type      VARCHAR(50) NOT NULL,
    severity        VARCHAR(20) NOT NULL,
    source          VARCHAR(20) NOT NULL,
    source_alert_id VARCHAR(100),
    title           VARCHAR(200),
    description     TEXT,
    affected_area   VARCHAR(200),
    expires_at      TIMESTAMPTZ,
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_alerts_type ON hazard_alerts(alert_type);
CREATE INDEX idx_alerts_time ON hazard_alerts(alert_time DESC);
