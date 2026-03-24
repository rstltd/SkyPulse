-- ==========================================
-- Continuous Aggregates: hourly rainfall summary
-- Must run outside transaction (Flyway non-transactional)
-- ==========================================

CREATE MATERIALIZED VIEW hourly_rainfall_summary
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', time) AS bucket,
    station_code,
    SUM(precipitation) AS hourly_precipitation,
    COUNT(*) AS record_count
FROM rainfall_observations
GROUP BY bucket, station_code
WITH NO DATA;

SELECT add_continuous_aggregate_policy('hourly_rainfall_summary',
    start_offset    => INTERVAL '3 hours',
    end_offset      => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour');

-- ==========================================
-- Continuous Aggregates: accumulated rainfall (3h/24h/48h/72h)
-- Core landslide warning metric
-- ==========================================

CREATE MATERIALIZED VIEW accumulated_rainfall_3h
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', time) AS bucket,
    station_code,
    SUM(precipitation) AS accumulated_3h
FROM rainfall_observations
WHERE time > time_bucket('1 hour', time) - INTERVAL '3 hours'
GROUP BY bucket, station_code
WITH NO DATA;

SELECT add_continuous_aggregate_policy('accumulated_rainfall_3h',
    start_offset    => INTERVAL '6 hours',
    end_offset      => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour');

CREATE MATERIALIZED VIEW accumulated_rainfall_24h
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', time) AS bucket,
    station_code,
    SUM(precipitation) AS accumulated_24h
FROM rainfall_observations
WHERE time > time_bucket('1 hour', time) - INTERVAL '24 hours'
GROUP BY bucket, station_code
WITH NO DATA;

SELECT add_continuous_aggregate_policy('accumulated_rainfall_24h',
    start_offset    => INTERVAL '27 hours',
    end_offset      => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour');

CREATE MATERIALIZED VIEW accumulated_rainfall_48h
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', time) AS bucket,
    station_code,
    SUM(precipitation) AS accumulated_48h
FROM rainfall_observations
WHERE time > time_bucket('1 hour', time) - INTERVAL '48 hours'
GROUP BY bucket, station_code
WITH NO DATA;

SELECT add_continuous_aggregate_policy('accumulated_rainfall_48h',
    start_offset    => INTERVAL '51 hours',
    end_offset      => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour');

CREATE MATERIALIZED VIEW accumulated_rainfall_72h
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 hour', time) AS bucket,
    station_code,
    SUM(precipitation) AS accumulated_72h
FROM rainfall_observations
WHERE time > time_bucket('1 hour', time) - INTERVAL '72 hours'
GROUP BY bucket, station_code
WITH NO DATA;

SELECT add_continuous_aggregate_policy('accumulated_rainfall_72h',
    start_offset    => INTERVAL '75 hours',
    end_offset      => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour');
