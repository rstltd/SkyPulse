-- ============================================================================
-- Phase 2 clean baseline (6/9): hazard alerts + SWCB debris-flow warning baselines.
-- ============================================================================

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
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_alerts_type ON hazard_alerts (alert_type);
CREATE INDEX idx_alerts_time ON hazard_alerts (alert_time DESC);

-- SWCB debris-flow reference streams + alert baselines (GetDebrisRainData). alert_value is the
-- R70 effective-rainfall threshold (mm). Two reference rainfall stations with blend ratios.
CREATE TABLE debris_stream (
    debris_no    VARCHAR(20) PRIMARY KEY,
    county       VARCHAR(20),
    town         VARCHAR(20),
    village      VARCHAR(30),
    alert_value  DECIMAL(7,2),
    ref_station1 VARCHAR(40),
    ref_ratio1   DECIMAL(6,3),
    ref_station2 VARCHAR(40),
    ref_ratio2   DECIMAL(6,3),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_debris_county_town ON debris_stream (county, town);

-- SWCB township-level alert baseline (GetCountyTownAlertValueList) — the simpler lookup used
-- first for the coordinate -> township -> warning-light chain.
CREATE TABLE township_alert_baseline (
    county      VARCHAR(20) NOT NULL,
    town        VARCHAR(20) NOT NULL,
    alert_value DECIMAL(7,2),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (county, town)
);
