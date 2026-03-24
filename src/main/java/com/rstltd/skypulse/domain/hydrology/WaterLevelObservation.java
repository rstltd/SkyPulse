package com.rstltd.skypulse.domain.hydrology;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @Column(name = "station_code", nullable = false, length = 30)
    private String stationCode;

    @Column(name = "water_level", precision = 8, scale = 3)
    private BigDecimal waterLevel;

    @Column(length = 20)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private String rawData;

    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;
}
