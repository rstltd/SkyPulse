package com.rstltd.skypulse.domain.spaceweather;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Continuously-sampled NOAA G/R/S space-weather scales (SWPC products/noaa-scales.json).
 * Keyed by (time, horizon) where horizon is {@code observed / predicted_d1 / d2 / d3}.
 * Feeds the GNSS-quality G branch, which was always null when only alert-parsed scales existed.
 */
@Entity
@Table(name = "noaa_scales")
@IdClass(NoaaScaleId.class)
@Getter @Setter @NoArgsConstructor
public class NoaaScale {

    @Id
    @Column(nullable = false)
    private OffsetDateTime time;

    @Id
    @Column(nullable = false, length = 12)
    private String horizon;

    @Column(name = "g_scale")
    private Integer gScale;

    @Column(name = "r_scale")
    private Integer rScale;

    @Column(name = "s_scale")
    private Integer sScale;

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
