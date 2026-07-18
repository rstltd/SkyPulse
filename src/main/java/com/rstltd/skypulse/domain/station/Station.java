package com.rstltd.skypulse.domain.station;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Generic station spatial dimension (one row per code). The kinds of data a station reports live
 * in {@link StationCapability}; water-level alert thresholds live in {@link WaterLevelStation}.
 */
@Entity
@Table(name = "stations")
@Getter @Setter @NoArgsConstructor
public class Station {

    @Id
    @Column(name = "station_code", length = 40)
    private String stationCode;

    @Column(name = "station_name", nullable = false, length = 100)
    private String stationName;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "altitude_m", precision = 7, scale = 2)
    private BigDecimal altitude;

    @Column(length = 20)
    private String county;

    @Column(length = 20)
    private String township;

    @Column(name = "is_active")
    private Boolean isActive;

    @JsonIgnore
    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    @JsonIgnore
    @Column(name = "updated_at", insertable = false)
    private OffsetDateTime updatedAt;
}
