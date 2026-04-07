-- Add detailed rainfall columns from CWA API
-- precipitation (existing) stores "Now" (daily accumulated)
-- New columns store individual period values

ALTER TABLE rainfall_observations
    ADD COLUMN IF NOT EXISTS precip_10min  DECIMAL(8,2),
    ADD COLUMN IF NOT EXISTS precip_1hr   DECIMAL(8,2),
    ADD COLUMN IF NOT EXISTS precip_3hr   DECIMAL(8,2),
    ADD COLUMN IF NOT EXISTS precip_6hr   DECIMAL(8,2),
    ADD COLUMN IF NOT EXISTS precip_12hr  DECIMAL(8,2),
    ADD COLUMN IF NOT EXISTS precip_24hr  DECIMAL(8,2);

COMMENT ON COLUMN rainfall_observations.precipitation IS 'Daily accumulated rainfall (CWA Now)';
COMMENT ON COLUMN rainfall_observations.precip_10min IS 'Past 10 minutes rainfall';
COMMENT ON COLUMN rainfall_observations.precip_1hr IS 'Past 1 hour rainfall';
COMMENT ON COLUMN rainfall_observations.precip_3hr IS 'Past 3 hours rainfall';
COMMENT ON COLUMN rainfall_observations.precip_6hr IS 'Past 6 hours rainfall';
COMMENT ON COLUMN rainfall_observations.precip_12hr IS 'Past 12 hours rainfall';
COMMENT ON COLUMN rainfall_observations.precip_24hr IS 'Past 24 hours rainfall';
