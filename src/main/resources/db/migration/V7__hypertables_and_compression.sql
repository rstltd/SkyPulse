-- ============================================================================
-- Phase 2 clean baseline (7/9): hypertables + compression + retention.
-- Runs after all time-series tables exist. Unique keys already include the partition
-- column (time), satisfying TimescaleDB.
-- ============================================================================

SELECT create_hypertable('rainfall_observations',    'time', chunk_time_interval => INTERVAL '1 day');
SELECT create_hypertable('weather_observations',     'time', chunk_time_interval => INTERVAL '1 day');
SELECT create_hypertable('water_level_observations', 'time', chunk_time_interval => INTERVAL '1 day');
SELECT create_hypertable('reservoir_status',         'time', chunk_time_interval => INTERVAL '7 days');
SELECT create_hypertable('kp_index_records',         'time', chunk_time_interval => INTERVAL '7 days');
SELECT create_hypertable('dst_index_records',        'time', chunk_time_interval => INTERVAL '7 days');
SELECT create_hypertable('solar_wind_records',       'time', chunk_time_interval => INTERVAL '7 days');
SELECT create_hypertable('noaa_scales',              'time', chunk_time_interval => INTERVAL '7 days');
SELECT create_hypertable('earthquake_events',        'time', chunk_time_interval => INTERVAL '7 days');

-- earthquake_events dedup key (must include the partition column). Very low volume -> no compression.
ALTER TABLE earthquake_events
    ADD CONSTRAINT earthquake_events_event_id_time_unique UNIQUE (event_id, time);

-- Compression (segment by the natural series key where present).
ALTER TABLE rainfall_observations SET (timescaledb.compress,
    timescaledb.compress_segmentby = 'station_code', timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('rainfall_observations', INTERVAL '3 days');

ALTER TABLE water_level_observations SET (timescaledb.compress,
    timescaledb.compress_segmentby = 'station_code', timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('water_level_observations', INTERVAL '3 days');

ALTER TABLE weather_observations SET (timescaledb.compress,
    timescaledb.compress_segmentby = 'station_code', timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('weather_observations', INTERVAL '7 days');

ALTER TABLE reservoir_status SET (timescaledb.compress,
    timescaledb.compress_segmentby = 'reservoir_id', timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('reservoir_status', INTERVAL '7 days');

ALTER TABLE kp_index_records SET (timescaledb.compress, timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('kp_index_records', INTERVAL '7 days');

ALTER TABLE dst_index_records SET (timescaledb.compress, timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('dst_index_records', INTERVAL '7 days');

ALTER TABLE solar_wind_records SET (timescaledb.compress, timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('solar_wind_records', INTERVAL '7 days');

ALTER TABLE noaa_scales SET (timescaledb.compress, timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('noaa_scales', INTERVAL '7 days');

-- Retention: weather kept 2 years; high-frequency rainfall/water level kept indefinitely
-- (cost is low on 256 GB SSD, and forward-only 10-min history is precious).
SELECT add_retention_policy('weather_observations', INTERVAL '730 days');
