package com.rstltd.skypulse.domain.station;

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
 * Water-level alert thresholds and river metadata, moved out of {@code stations} (they only
 * apply to WATER_LEVEL stations). Keyed on the station code.
 */
@Entity
@Table(name = "water_level_station")
@Getter @Setter @NoArgsConstructor
public class WaterLevelStation {

    @Id
    @Column(name = "station_code", length = 40)
    private String stationCode;

    @Column(name = "river_name", length = 50)
    private String riverName;

    @Column(length = 50)
    private String basin;

    @Column(name = "alert_level1", precision = 8, scale = 3)
    private BigDecimal alertLevel1;

    @Column(name = "alert_level2", precision = 8, scale = 3)
    private BigDecimal alertLevel2;

    @Column(name = "alert_level3", precision = 8, scale = 3)
    private BigDecimal alertLevel3;

    @Column(name = "updated_at", insertable = false)
    private OffsetDateTime updatedAt;
}
