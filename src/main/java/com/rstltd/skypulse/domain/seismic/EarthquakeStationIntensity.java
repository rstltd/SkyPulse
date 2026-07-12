package com.rstltd.skypulse.domain.seismic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Per-station shaking for an earthquake event (CWA Intensity.ShakingArea/EqStation).
 * Keyed by (event_id, station_code); {@code station_lat/lon} support
 * coordinate -> nearest-station intensity lookups via auto-radius.
 */
@Entity
@Table(name = "earthquake_station_intensity")
@IdClass(EarthquakeStationIntensityId.class)
@Getter @Setter @NoArgsConstructor
public class EarthquakeStationIntensity {

    @Id
    @Column(name = "event_id", nullable = false, length = 50)
    private String eventId;

    @Id
    @Column(name = "station_code", nullable = false, length = 40)
    private String stationCode;

    @Column(nullable = false)
    private OffsetDateTime time;

    @Column(name = "station_name", length = 60)
    private String stationName;

    @Column(length = 20)
    private String county;

    @Column(length = 6)
    private String intensity;

    @Column(name = "intensity_rank")
    private Short intensityRank;

    @Column(name = "pga_gal", precision = 7, scale = 2)
    private BigDecimal pgaGal;

    @Column(name = "pgv_cms", precision = 7, scale = 2)
    private BigDecimal pgvCms;

    @Column(name = "station_lat", precision = 9, scale = 6)
    private BigDecimal stationLat;

    @Column(name = "station_lon", precision = 9, scale = 6)
    private BigDecimal stationLon;
}
