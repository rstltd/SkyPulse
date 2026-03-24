CREATE TABLE rainfall_observations (
    time            TIMESTAMPTZ NOT NULL,
    station_code    VARCHAR(30) NOT NULL,
    precipitation   DECIMAL(8,2),
    source          VARCHAR(20) NOT NULL,
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE weather_observations (
    time            TIMESTAMPTZ NOT NULL,
    station_code    VARCHAR(30) NOT NULL,
    temperature     DECIMAL(5,2),
    humidity        DECIMAL(5,2),
    pressure        DECIMAL(7,2),
    wind_speed      DECIMAL(6,2),
    wind_direction  DECIMAL(5,2),
    precipitation   DECIMAL(8,2),
    source          VARCHAR(20) NOT NULL,
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE weather_forecasts (
    id              BIGSERIAL PRIMARY KEY,
    location_name   VARCHAR(50) NOT NULL,
    forecast_time   TIMESTAMPTZ NOT NULL,
    issued_time     TIMESTAMPTZ NOT NULL,
    weather_desc    VARCHAR(100),
    min_temp        DECIMAL(5,2),
    max_temp        DECIMAL(5,2),
    rain_prob       INTEGER,
    source          VARCHAR(20) DEFAULT 'CWA',
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
