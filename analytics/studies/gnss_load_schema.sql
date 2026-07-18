-- Target schema for loading the production GNSS solutions (schma/pos_collect_realtime.sql,
-- a MariaDB dump, git-ignored) into SkyPulse's TimescaleDB as a hypertable.
-- Created out-of-band (NOT via Flyway — this is lab data infra, not the Spring app's schema).
-- See docs/GNSS_DATA.md. Load with analytics/studies/gnss_load_convert.py.

DROP TABLE IF EXISTS gnss_positions;
CREATE TABLE gnss_positions (
  area          varchar(64)  NOT NULL,
  station_id    varchar(64)  NOT NULL,
  result_time   timestamptz  NOT NULL,   -- solution epoch, UTC
  time_string   varchar(16),             -- processing window: 3hour / 6hour / 1hour
  ecef_x        numeric(12,4),           -- ECEF position (m) — the displacement signal
  ecef_y        numeric(12,4),
  ecef_z        numeric(12,4),
  sigma_x       numeric(8,4),
  sigma_y       numeric(8,4),
  sigma_z       numeric(8,4),
  fix_quality   smallint,
  sat_count     smallint,
  n_points      integer,
  is_valid      smallint,                -- 0 = failed fix; 1/2/3 = fix levels
  created_at    timestamptz,
  updated_at    timestamptz,
  ratio         numeric(7,2)             -- RTK ambiguity ratio (populated for recent rows only)
);
SELECT create_hypertable('gnss_positions', 'result_time', chunk_time_interval => INTERVAL '7 days');
