-- Remove the 5 continuous aggregates created in V8. They have zero Java references and
-- their definitions are broken: `WHERE time > time_bucket('1 hour', time) - INTERVAL 'Xh'`
-- is a per-row tautology, so accumulated_rainfall_3h/24h/48h/72h all equal the plain hourly
-- sum instead of a rolling window. True rolling-window rainfall is rebuilt in Phase 2
-- (10-minute moving window); accumulated rainfall is meanwhile computed in WeatherService.

SELECT remove_continuous_aggregate_policy('accumulated_rainfall_72h', if_not_exists => true);
SELECT remove_continuous_aggregate_policy('accumulated_rainfall_48h', if_not_exists => true);
SELECT remove_continuous_aggregate_policy('accumulated_rainfall_24h', if_not_exists => true);
SELECT remove_continuous_aggregate_policy('accumulated_rainfall_3h',  if_not_exists => true);
SELECT remove_continuous_aggregate_policy('hourly_rainfall_summary',  if_not_exists => true);

DROP MATERIALIZED VIEW IF EXISTS accumulated_rainfall_72h CASCADE;
DROP MATERIALIZED VIEW IF EXISTS accumulated_rainfall_48h CASCADE;
DROP MATERIALIZED VIEW IF EXISTS accumulated_rainfall_24h CASCADE;
DROP MATERIALIZED VIEW IF EXISTS accumulated_rainfall_3h  CASCADE;
DROP MATERIALIZED VIEW IF EXISTS hourly_rainfall_summary  CASCADE;
