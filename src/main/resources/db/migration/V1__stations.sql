-- ============================================================================
-- Phase 2 clean baseline (1/9): stations as a generic spatial dimension.
-- The image has no PostGIS; cube + earthdistance provide nearest-station queries.
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS cube;
CREATE EXTENSION IF NOT EXISTS earthdistance;

-- One row per station code. No single station_type (a code can report several
-- capabilities -> station_capability). Water-level alert thresholds live in
-- water_level_station, not here (they only apply to WATER_LEVEL stations).
CREATE TABLE stations (
    station_code VARCHAR(40) PRIMARY KEY,
    station_name VARCHAR(100) NOT NULL,
    source       VARCHAR(20)  NOT NULL,
    latitude     DECIMAL(9,6),
    longitude    DECIMAL(9,6),
    altitude_m   DECIMAL(7,2),
    county       VARCHAR(20),
    township     VARCHAR(20),
    is_active    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_stations_county   ON stations (county, township);
CREATE INDEX idx_stations_lat_lon  ON stations (latitude, longitude);
-- Nearest-station queries use earth_distance(ll_to_earth(...)) at query time; at ~1.4k
-- stations no functional GiST index is needed (a functional earthdistance GiST index can
-- be added later if query volume grows).

-- A station code can carry multiple capabilities. dataset_id records provenance;
-- last_seen supports the freshness/coverage transparency goal.
CREATE TABLE station_capability (
    station_code VARCHAR(40) NOT NULL REFERENCES stations(station_code) ON DELETE CASCADE,
    capability   VARCHAR(20) NOT NULL,   -- RAINFALL / WEATHER / WATER_LEVEL
    dataset_id   VARCHAR(60),            -- e.g. O-A0002-001
    first_seen   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (station_code, capability)
);
CREATE INDEX idx_station_capability_cap ON station_capability (capability);

-- Water-level alert thresholds, moved out of stations.
CREATE TABLE water_level_station (
    station_code VARCHAR(40) PRIMARY KEY REFERENCES stations(station_code) ON DELETE CASCADE,
    river_name   VARCHAR(50),
    basin        VARCHAR(50),
    alert_level1 DECIMAL(8,3),
    alert_level2 DECIMAL(8,3),
    alert_level3 DECIMAL(8,3),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
