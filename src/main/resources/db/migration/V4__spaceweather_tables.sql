-- ============================================================================
-- Phase 2 clean baseline (4/9): space weather (Kp/Dst/solar wind + NOAA G/R/S).
-- kp/dst/solar_wind are global (one row per timestamp) -> UNIQUE(time) also protects dedup.
-- ============================================================================

CREATE TABLE kp_index_records (
    time       TIMESTAMPTZ NOT NULL,
    kp_value   DECIMAL(3,1) NOT NULL,
    source     VARCHAR(20) DEFAULT 'SWPC',
    raw_data   JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT kp_time_unique UNIQUE (time)
);

CREATE TABLE dst_index_records (
    time       TIMESTAMPTZ NOT NULL,
    dst_value  DECIMAL(6,1) NOT NULL,
    source     VARCHAR(20) DEFAULT 'WDC_KYOTO',
    raw_data   JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT dst_time_unique UNIQUE (time)
);

CREATE TABLE solar_wind_records (
    time       TIMESTAMPTZ NOT NULL,
    wind_speed DECIMAL(8,2),
    density    DECIMAL(8,2),
    bz         DECIMAL(8,2),
    bt         DECIMAL(8,2),
    source     VARCHAR(20) DEFAULT 'SWPC',
    raw_data   JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT solar_wind_time_unique UNIQUE (time)
);

CREATE TABLE space_weather_alerts (
    id            BIGSERIAL PRIMARY KEY,
    alert_time    TIMESTAMPTZ NOT NULL,
    alert_type    VARCHAR(50),
    serial_number VARCHAR(30),
    message       TEXT,
    g_scale       INTEGER,
    r_scale       INTEGER,
    s_scale       INTEGER,
    source        VARCHAR(20) DEFAULT 'SWPC',
    raw_data      JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Continuously-sampled NOAA G/R/S scales (from products/noaa-scales.json). Populates the
-- G/R/S that were always null before (SwpcAlertCollector never parsed them), so the
-- GNSS-quality G branch actually works. horizon: observed / predicted_d1 / d2 / d3.
CREATE TABLE noaa_scales (
    time       TIMESTAMPTZ NOT NULL,
    horizon    VARCHAR(12) NOT NULL,
    g_scale    INTEGER,
    r_scale    INTEGER,
    s_scale    INTEGER,
    source     VARCHAR(20) NOT NULL DEFAULT 'SWPC',
    raw_data   JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT noaa_scales_time_horizon_unique UNIQUE (time, horizon)
);
