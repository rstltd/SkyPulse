CREATE TABLE stations (
    id              BIGSERIAL PRIMARY KEY,
    station_code    VARCHAR(30) NOT NULL UNIQUE,
    station_name    VARCHAR(100) NOT NULL,
    source          VARCHAR(20) NOT NULL,
    station_type    VARCHAR(30) NOT NULL,
    latitude        DECIMAL(9,6),
    longitude       DECIMAL(9,6),
    altitude        DECIMAL(7,2),
    county          VARCHAR(20),
    township        VARCHAR(20),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_stations_source ON stations(source);
CREATE INDEX idx_stations_type ON stations(station_type);
