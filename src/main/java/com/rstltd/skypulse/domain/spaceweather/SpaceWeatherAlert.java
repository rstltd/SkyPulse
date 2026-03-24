package com.rstltd.skypulse.domain.spaceweather;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "space_weather_alerts")
@Getter @Setter @NoArgsConstructor
public class SpaceWeatherAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_time", nullable = false)
    private OffsetDateTime alertTime;

    @Column(name = "alert_type", length = 50)
    private String alertType;

    @Column(name = "serial_number", length = 30)
    private String serialNumber;

    @Column(columnDefinition = "text")
    private String message;

    @Column(name = "g_scale")
    private Integer gScale;

    @Column(name = "s_scale")
    private Integer sScale;

    @Column(name = "r_scale")
    private Integer rScale;

    @Column(length = 20)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private String rawData;

    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;
}
