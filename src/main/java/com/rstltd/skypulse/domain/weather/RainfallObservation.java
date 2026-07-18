package com.rstltd.skypulse.domain.weather;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    @Column(name = "station_code", nullable = false, length = 40)
    private String stationCode;

    /** Past 10 minutes (non-overlapping) — the accumulation base; SUM for rolling windows. */
    @Column(name = "rain_10min_mm", precision = 6, scale = 2)
    private BigDecimal rain10minMm;

    /** CWA "Now": daily cumulative (resets at local midnight). Must never be summed across rows. */
    @Column(name = "daily_accum_mm", precision = 7, scale = 2)
    private BigDecimal dailyAccumMm;

    // CWA trailing-window snapshots — kept for cross-check / fallback, never summed across rows.
    @Column(name = "trailing_1hr_mm", precision = 6, scale = 2)
    private BigDecimal trailing1hrMm;

    @Column(name = "trailing_3hr_mm", precision = 6, scale = 2)
    private BigDecimal trailing3hrMm;

    @Column(name = "trailing_6hr_mm", precision = 6, scale = 2)
    private BigDecimal trailing6hrMm;

    @Column(name = "trailing_12hr_mm", precision = 7, scale = 2)
    private BigDecimal trailing12hrMm;

    @Column(name = "trailing_24hr_mm", precision = 7, scale = 2)
    private BigDecimal trailing24hrMm;

    @Column(nullable = false, length = 20)
    private String source;

    @JsonIgnore
    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;
}
