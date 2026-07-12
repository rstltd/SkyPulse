-- ============================================================================
-- Phase 2 clean baseline (9/9): collector execution audit log.
-- ============================================================================

CREATE TABLE system_logs (
    id              BIGSERIAL,
    time            TIMESTAMPTZ  NOT NULL,
    category        VARCHAR(20)  NOT NULL,
    level           VARCHAR(10)  NOT NULL,
    source          VARCHAR(50)  NOT NULL,
    message         VARCHAR(500),
    fetched_count   INT,
    valid_count     INT,
    persisted_count INT,
    duration_ms     BIGINT,
    error_detail    TEXT,
    created_at      TIMESTAMPTZ  DEFAULT NOW()
);

SELECT create_hypertable('system_logs', 'time');

CREATE INDEX idx_system_logs_category ON system_logs (category, time DESC);
CREATE INDEX idx_system_logs_source   ON system_logs (source, time DESC);
CREATE INDEX idx_system_logs_level    ON system_logs (level, time DESC);

ALTER TABLE system_logs SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'category, source',
    timescaledb.compress_orderby   = 'time DESC');
SELECT add_compression_policy('system_logs', INTERVAL '7 days');

SELECT add_retention_policy('system_logs', INTERVAL '90 days');
