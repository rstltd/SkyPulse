-- ============================================================================
-- Phase 2 clean baseline (3/9): earthquakes + per-station intensity.
-- ============================================================================

-- max_intensity is the CWA shindo scale (0,1,2,3,4,5-,5+,6-,6+,7); max_intensity_rank is a
-- 0..9 normalization for sorting/filtering. Very low volume -> not compressed.
CREATE TABLE earthquake_events (
    time               TIMESTAMPTZ NOT NULL,
    event_id           VARCHAR(50) NOT NULL,
    magnitude          DECIMAL(4,2) NOT NULL,
    depth_km           DECIMAL(7,2),
    latitude           DECIMAL(9,6) NOT NULL,
    longitude          DECIMAL(9,6) NOT NULL,
    location_desc      VARCHAR(200),
    max_intensity      VARCHAR(6),
    max_intensity_rank SMALLINT,
    source             VARCHAR(20) NOT NULL,
    raw_data           JSONB,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Per-station shaking for an event (CWA Intensity.ShakingArea/EqStation). Supports
-- coordinate -> nearest-station intensity via auto-radius. Low volume, not a hypertable.
CREATE TABLE earthquake_station_intensity (
    event_id       VARCHAR(50) NOT NULL,
    time           TIMESTAMPTZ NOT NULL,
    station_code   VARCHAR(40) NOT NULL,
    station_name   VARCHAR(60),
    county         VARCHAR(20),
    intensity      VARCHAR(6),
    intensity_rank SMALLINT,
    pga_gal        DECIMAL(7,2),
    pgv_cms        DECIMAL(7,2),
    station_lat    DECIMAL(9,6),
    station_lon    DECIMAL(9,6),
    PRIMARY KEY (event_id, station_code)
);
CREATE INDEX idx_eq_station_intensity_lat_lon ON earthquake_station_intensity (station_lat, station_lon);
CREATE INDEX idx_eq_station_intensity_event   ON earthquake_station_intensity (event_id);
