-- ==========================================
-- Hypertable
-- ==========================================

SELECT create_hypertable('rainfall_observations', 'time');
SELECT create_hypertable('weather_observations', 'time');
SELECT create_hypertable('kp_index_records', 'time');
SELECT create_hypertable('dst_index_records', 'time');
SELECT create_hypertable('solar_wind_records', 'time');
SELECT create_hypertable('water_level_observations', 'time');
SELECT create_hypertable('reservoir_statuses', 'time');
SELECT create_hypertable('earthquake_events', 'time');

-- Unique constraint on event_id must include time (TimescaleDB requirement)
ALTER TABLE earthquake_events ADD CONSTRAINT earthquake_events_event_id_time_unique UNIQUE (event_id, time);

-- ==========================================
-- Compression (auto-compress after 7 days, data retained permanently)
-- ==========================================

ALTER TABLE rainfall_observations SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'station_code',
    timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('rainfall_observations', INTERVAL '7 days');

ALTER TABLE weather_observations SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'station_code',
    timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('weather_observations', INTERVAL '7 days');

ALTER TABLE water_level_observations SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'station_code',
    timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('water_level_observations', INTERVAL '7 days');

ALTER TABLE kp_index_records SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('kp_index_records', INTERVAL '7 days');

ALTER TABLE dst_index_records SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('dst_index_records', INTERVAL '7 days');

ALTER TABLE solar_wind_records SET (
    timescaledb.compress,
    timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('solar_wind_records', INTERVAL '7 days');

ALTER TABLE reservoir_statuses SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'reservoir_id',
    timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('reservoir_statuses', INTERVAL '7 days');

-- earthquake_events: very low volume (~5-10 rows/day), no compression needed
