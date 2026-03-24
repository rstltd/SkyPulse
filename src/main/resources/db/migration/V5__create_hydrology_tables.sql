CREATE TABLE water_level_observations (
    time            TIMESTAMPTZ NOT NULL,
    station_code    VARCHAR(30) NOT NULL,
    water_level     DECIMAL(8,3),
    source          VARCHAR(20) DEFAULT 'WRA',
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE reservoir_statuses (
    time            TIMESTAMPTZ NOT NULL,
    reservoir_id    VARCHAR(20) NOT NULL,
    reservoir_name  VARCHAR(50),
    water_level     DECIMAL(8,3),
    full_level      DECIMAL(8,3),
    storage_pct     DECIMAL(5,2),
    inflow          DECIMAL(10,2),
    outflow         DECIMAL(10,2),
    daily_rainfall  DECIMAL(8,2),
    source          VARCHAR(20) DEFAULT 'WRA',
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
