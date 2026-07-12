-- Mirror V9 for the hydrology hypertables: add the unique keys that de-duplication relies on
-- (V9 added them for rainfall/weather but omitted water level and reservoirs). The unique key
-- must include the partitioning column (time) per TimescaleDB. Collectors still merge via
-- exists-check before insert, so this only hardens the DB against duplicates.
ALTER TABLE water_level_observations
    ADD CONSTRAINT water_level_obs_time_station_unique UNIQUE (time, station_code);
ALTER TABLE reservoir_statuses
    ADD CONSTRAINT reservoir_status_time_id_unique UNIQUE (time, reservoir_id);
