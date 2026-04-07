-- Add water level alert thresholds to stations table
ALTER TABLE stations ADD COLUMN alert_level1 DECIMAL(8,3);
ALTER TABLE stations ADD COLUMN alert_level2 DECIMAL(8,3);
ALTER TABLE stations ADD COLUMN alert_level3 DECIMAL(8,3);
