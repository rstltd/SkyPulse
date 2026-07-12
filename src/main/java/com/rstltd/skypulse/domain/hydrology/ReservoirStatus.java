package com.rstltd.skypulse.domain.hydrology;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Reservoir time-series measurements. Static metadata (name, full level, design
 * capacity, coordinates) lives in the {@link Reservoir} dimension, joined on
 * {@code reservoir_id}.
 */
@Entity
@Table(name = "reservoir_status")
@IdClass(ReservoirStatusId.class)
@Getter @Setter @NoArgsConstructor
public class ReservoirStatus {

    @Id
    @Column(nullable = false)
    private OffsetDateTime time;

    @Id
    @Column(name = "reservoir_id", nullable = false, length = 20)
    private String reservoirId;

    @Column(name = "water_level_m", precision = 8, scale = 3)
    private BigDecimal waterLevelM;

    @Column(name = "effective_storage_m3", precision = 14, scale = 2)
    private BigDecimal effectiveStorageM3;

    @Column(name = "storage_pct", precision = 6, scale = 2)
    private BigDecimal storagePct;

    @Column(name = "inflow_cms", precision = 10, scale = 2)
    private BigDecimal inflowCms;

    @Column(name = "outflow_cms", precision = 10, scale = 2)
    private BigDecimal outflowCms;

    @Column(name = "catchment_rain_mm", precision = 7, scale = 2)
    private BigDecimal catchmentRainMm;

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
