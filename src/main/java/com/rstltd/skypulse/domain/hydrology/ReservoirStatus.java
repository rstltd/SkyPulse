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
@Table(name = "reservoir_statuses")
@IdClass(ReservoirStatusId.class)
@Getter @Setter @NoArgsConstructor
public class ReservoirStatus {

    @Id
    @Column(nullable = false)
    private OffsetDateTime time;

    @Id
    @Column(name = "reservoir_id", nullable = false, length = 20)
    private String reservoirId;

    @Column(name = "reservoir_name", length = 50)
    private String reservoirName;

    @Column(name = "water_level", precision = 8, scale = 3)
    private BigDecimal waterLevel;

    @Column(name = "full_level", precision = 8, scale = 3)
    private BigDecimal fullLevel;

    @Column(name = "storage_pct", precision = 5, scale = 2)
    private BigDecimal storagePct;

    @Column(precision = 10, scale = 2)
    private BigDecimal inflow;

    @Column(precision = 10, scale = 2)
    private BigDecimal outflow;

    @Column(name = "daily_rainfall", precision = 8, scale = 2)
    private BigDecimal dailyRainfall;

    @Column(length = 20)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private String rawData;

    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;
}
