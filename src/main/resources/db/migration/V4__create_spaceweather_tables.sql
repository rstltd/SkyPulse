CREATE TABLE kp_index_records (
    time            TIMESTAMPTZ NOT NULL,
    kp_value        DECIMAL(3,1) NOT NULL,
    source          VARCHAR(20) DEFAULT 'SWPC',
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE dst_index_records (
    time            TIMESTAMPTZ NOT NULL,
    dst_value       DECIMAL(6,1) NOT NULL,
    source          VARCHAR(20) DEFAULT 'WDC_KYOTO',
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE solar_wind_records (
    time            TIMESTAMPTZ NOT NULL,
    wind_speed      DECIMAL(8,2),
    density         DECIMAL(8,2),
    bz              DECIMAL(8,2),
    bt              DECIMAL(8,2),
    source          VARCHAR(20) DEFAULT 'SWPC',
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE space_weather_alerts (
    id              BIGSERIAL PRIMARY KEY,
    alert_time      TIMESTAMPTZ NOT NULL,
    alert_type      VARCHAR(50),
    serial_number   VARCHAR(30),
    message         TEXT,
    g_scale         INTEGER,
    s_scale         INTEGER,
    r_scale         INTEGER,
    source          VARCHAR(20) DEFAULT 'SWPC',
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
