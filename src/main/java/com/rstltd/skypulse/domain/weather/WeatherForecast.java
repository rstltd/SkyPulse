package com.rstltd.skypulse.domain.weather;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "weather_forecasts")
@Getter @Setter @NoArgsConstructor
public class WeatherForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "location_name", nullable = false, length = 50)
    private String locationName;

    @Column(name = "forecast_time", nullable = false)
    private OffsetDateTime forecastTime;

    @Column(name = "issued_time", nullable = false)
    private OffsetDateTime issuedTime;

    @Column(name = "weather_desc", length = 100)
    private String weatherDesc;

    @Column(name = "min_temp", precision = 5, scale = 2)
    private BigDecimal minTemp;

    @Column(name = "max_temp", precision = 5, scale = 2)
    private BigDecimal maxTemp;

    @Column(name = "rain_prob")
    private Integer rainProb;

    @Column(length = 20)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private String rawData;

    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;
}
