package com.rstltd.skypulse.domain.spaceweather;

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
@Table(name = "solar_wind_records")
@Getter @Setter @NoArgsConstructor
public class SolarWindRecord {

    @Id
    @Column(nullable = false)
    private OffsetDateTime time;

    @Column(name = "wind_speed", precision = 8, scale = 2)
    private BigDecimal windSpeed;

    @Column(precision = 8, scale = 2)
    private BigDecimal density;

    @Column(precision = 8, scale = 2)
    private BigDecimal bz;

    @Column(precision = 8, scale = 2)
    private BigDecimal bt;

    @Column(length = 20)
    private String source;

    @JsonIgnore
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private String rawData;

    @JsonIgnore
    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;
}
