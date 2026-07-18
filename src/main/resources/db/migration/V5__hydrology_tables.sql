-- ============================================================================
-- Phase 2 clean baseline (5/9): water level + reservoirs (dimension / status split).
-- ============================================================================

-- Water level at 10-minute resolution. No raw_data (high-frequency table).
CREATE TABLE water_level_observations (
    time         TIMESTAMPTZ NOT NULL,
    station_code VARCHAR(40) NOT NULL,
    water_level  DECIMAL(8,3),
    source       VARCHAR(20) DEFAULT 'WRA',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT water_level_obs_time_station_unique UNIQUE (time, station_code)
);

-- Static reservoir dimension (name, capacity, coordinates). Coordinates sourced from the
-- Ministry of Environment GISEPA_P_27 dataset (already WGS84) + a curated seed for gaps.
CREATE TABLE reservoirs (
    reservoir_id       VARCHAR(20) PRIMARY KEY,
    reservoir_name     VARCHAR(50),
    full_level_m       DECIMAL(8,3),
    design_capacity_m3 DECIMAL(14,2),
    latitude           DECIMAL(9,6),
    longitude          DECIMAL(9,6),
    basin              VARCHAR(50),
    county             VARCHAR(20),
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_reservoirs_lat_lon ON reservoirs (latitude, longitude);

-- Reservoir time-series status; static metadata moved to reservoirs. storage_pct widened to
-- DECIMAL(6,2) (old DECIMAL(5,2) overflowed) and is better recomputed at read time.
CREATE TABLE reservoir_status (
    time                 TIMESTAMPTZ NOT NULL,
    reservoir_id         VARCHAR(20) NOT NULL,
    water_level_m        DECIMAL(8,3),
    effective_storage_m3 DECIMAL(14,2),
    storage_pct          DECIMAL(6,2),
    inflow_cms           DECIMAL(10,2),
    outflow_cms          DECIMAL(10,2),
    catchment_rain_mm    DECIMAL(7,2),
    source               VARCHAR(20) NOT NULL DEFAULT 'WRA',
    raw_data             JSONB,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT reservoir_status_time_id_unique UNIQUE (time, reservoir_id)
);
