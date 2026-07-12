package com.rstltd.skypulse.domain.hydrology;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "water_level_observations")
@IdClass(WaterLevelObservationId.class)
@Getter @Setter @NoArgsConstructor
public class WaterLevelObservation {

    @Id
    @Column(nullable = false)
    private OffsetDateTime time;

    @Id
    @Column(name = "station_code", nullable = false, length = 40)
    private String stationCode;

    @Column(name = "water_level", precision = 8, scale = 3)
    private BigDecimal waterLevel;

    @Column(length = 20)
    private String source;

    @JsonIgnore
    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;
}
