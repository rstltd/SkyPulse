CREATE TABLE earthquake_events (
    time            TIMESTAMPTZ NOT NULL,
    event_id        VARCHAR(50) NOT NULL,
    magnitude       DECIMAL(4,2) NOT NULL,
    depth_km        DECIMAL(7,2),
    latitude        DECIMAL(9,6) NOT NULL,
    longitude       DECIMAL(9,6) NOT NULL,
    location_desc   VARCHAR(200),
    source          VARCHAR(20) NOT NULL,
    max_intensity   VARCHAR(10),
    raw_data        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
