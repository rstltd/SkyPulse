-- Unique constraints for UPSERT deduplication on hypertables
-- Must include partitioning column (time) per TimescaleDB requirement

ALTER TABLE rainfall_observations
    ADD CONSTRAINT rainfall_obs_time_station_unique UNIQUE (time, station_code);

ALTER TABLE weather_observations
    ADD CONSTRAINT weather_obs_time_station_unique UNIQUE (time, station_code);
