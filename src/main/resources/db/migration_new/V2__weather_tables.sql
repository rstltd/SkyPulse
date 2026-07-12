-- ============================================================================
-- Phase 2 clean baseline (2/9): rainfall / weather / forecasts.
-- ============================================================================

-- Rainfall at 10-minute resolution. rain_10min_mm (past 10 min, non-overlapping) is the
-- accumulation base — SUM it for rolling windows. daily_accum_mm is CWA "Now" (resets at
-- local midnight) and must NEVER be summed across rows. trailing_* are CWA snapshot windows,
-- kept only for cross-check / fallback. No raw_data (high-frequency table, dropped by design).
CREATE TABLE rainfall_observations (
    time             TIMESTAMPTZ NOT NULL,
    station_code     VARCHAR(40) NOT NULL,
    rain_10min_mm    DECIMAL(6,2),
    daily_accum_mm   DECIMAL(7,2),
    trailing_1hr_mm  DECIMAL(6,2),
    trailing_3hr_mm  DECIMAL(6,2),
    trailing_6hr_mm  DECIMAL(6,2),
    trailing_12hr_mm DECIMAL(7,2),
    trailing_24hr_mm DECIMAL(7,2),
    source           VARCHAR(20) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT rainfall_obs_time_station_unique UNIQUE (time, station_code)
);

-- Weather observations (hourly). raw_data kept (lower volume). weather_desc captures the
-- CWA Weather text the old schema dropped.
CREATE TABLE weather_observations (
    time           TIMESTAMPTZ NOT NULL,
    station_code   VARCHAR(40) NOT NULL,
    temperature    DECIMAL(5,2),
    humidity       DECIMAL(5,2),
    pressure       DECIMAL(7,2),
    wind_speed     DECIMAL(6,2),
    wind_direction DECIMAL(5,2),
    precipitation  DECIMAL(8,2),
    weather_desc   VARCHAR(50),
    source         VARCHAR(20) NOT NULL,
    raw_data       JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT weather_obs_time_station_unique UNIQUE (time, station_code)
);

-- Weekly township forecasts. Natural key prevents the unbounded duplicate growth of the
-- old schema (which re-inserted the whole forecast every 6h with a new issued_time).
CREATE TABLE weather_forecasts (
    id            BIGSERIAL PRIMARY KEY,
    location_name VARCHAR(50) NOT NULL,
    forecast_time TIMESTAMPTZ NOT NULL,
    issued_time   TIMESTAMPTZ NOT NULL,
    weather_desc  VARCHAR(100),
    min_temp      DECIMAL(5,2),
    max_temp      DECIMAL(5,2),
    rain_prob     INTEGER,
    source        VARCHAR(20) DEFAULT 'CWA',
    raw_data      JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT weather_forecast_unique UNIQUE (location_name, forecast_time, issued_time)
);
CREATE INDEX idx_forecast_location_time ON weather_forecasts (location_name, forecast_time);
