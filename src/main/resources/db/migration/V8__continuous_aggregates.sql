-- ============================================================================
-- Phase 2 clean baseline (8/9): CORRECT continuous aggregates.
-- Must run outside a transaction (see V8__continuous_aggregates.sql.conf).
--
-- Unlike the old V8 (whose `WHERE time > time_bucket('1 hour', time) - INTERVAL 'Nh'` was a
-- per-row tautology that made every window equal the hourly sum), these are plain fixed-bucket
-- aggregates. Rolling 3/6/12/24/48/72h windows are computed at READ time with window functions
-- over rainfall_hourly (small: <= 168 rows/station/week).
-- ============================================================================

CREATE MATERIALIZED VIEW rainfall_hourly
WITH (timescaledb.continuous) AS
SELECT time_bucket('1 hour', time) AS bucket,
       station_code,
       SUM(rain_10min_mm) AS rain_mm,
       COUNT(*)           AS sample_count   -- completeness signal (should be 6 per full hour)
FROM rainfall_observations
GROUP BY bucket, station_code
WITH NO DATA;

SELECT add_continuous_aggregate_policy('rainfall_hourly',
    start_offset      => INTERVAL '3 hours',
    end_offset        => INTERVAL '10 minutes',
    schedule_interval => INTERVAL '10 minutes');

-- Daily rainfall on the LOCAL calendar day (Asia/Taipei), matching SWCB's daily accounting for
-- the effective-accumulated-rainfall (Rt = R0 + sum 0.7^i * Ri) prior-day terms.
CREATE MATERIALIZED VIEW rainfall_daily
WITH (timescaledb.continuous) AS
SELECT time_bucket('1 day', time, 'Asia/Taipei') AS bucket,
       station_code,
       SUM(rain_10min_mm) AS rain_mm
FROM rainfall_observations
GROUP BY bucket, station_code
WITH NO DATA;

SELECT add_continuous_aggregate_policy('rainfall_daily',
    start_offset      => INTERVAL '8 days',
    end_offset        => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour');
