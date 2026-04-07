package com.rstltd.skypulse.domain.weather;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "rainfall_observations")
@IdClass(RainfallObservationId.class)
@Getter @Setter @NoArgsConstructor
public class RainfallObservation {

    @Id
    @Column(nullable = false)
    private OffsetDateTime time;

    @Id
    @Column(name = "station_code", nullable = false, length = 30)
    private String stationCode;

    @Column(precision = 8, scale = 2)
    private BigDecimal precipitation;

    @Column(name = "precip_10min", precision = 8, scale = 2)
    private BigDecimal precip10min;

    @Column(name = "precip_1hr", precision = 8, scale = 2)
    private BigDecimal precip1hr;

    @Column(name = "precip_3hr", precision = 8, scale = 2)
    private BigDecimal precip3hr;

    @Column(name = "precip_6hr", precision = 8, scale = 2)
    private BigDecimal precip6hr;

    @Column(name = "precip_12hr", precision = 8, scale = 2)
    private BigDecimal precip12hr;

    @Column(name = "precip_24hr", precision = 8, scale = 2)
    private BigDecimal precip24hr;

    @Column(nullable = false, length = 20)
    private String source;

    @JsonIgnore
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private String rawData;

    @JsonIgnore
    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;
}
