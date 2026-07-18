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
@Table(name = "weather_observations")
@IdClass(WeatherObservationId.class)
@Getter @Setter @NoArgsConstructor
public class WeatherObservation {

    @Id
    @Column(nullable = false)
    private OffsetDateTime time;

    @Id
    @Column(name = "station_code", nullable = false, length = 40)
    private String stationCode;

    @Column(precision = 5, scale = 2)
    private BigDecimal temperature;

    @Column(precision = 5, scale = 2)
    private BigDecimal humidity;

    @Column(precision = 7, scale = 2)
    private BigDecimal pressure;

    @Column(name = "wind_speed", precision = 6, scale = 2)
    private BigDecimal windSpeed;

    @Column(name = "wind_direction", precision = 5, scale = 2)
    private BigDecimal windDirection;

    @Column(precision = 8, scale = 2)
    private BigDecimal precipitation;

    /** CWA "Weather" text (e.g. 晴, 多雲, 陰有雨) — dropped by the old schema. */
    @Column(name = "weather_desc", length = 50)
    private String weatherDesc;

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
