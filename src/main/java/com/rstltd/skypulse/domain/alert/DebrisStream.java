package com.rstltd.skypulse.domain.alert;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * SWCB debris-flow reference stream + alert baseline (from GetDebrisRainData).
 * {@code alertValue} is the R70 effective-rainfall warning threshold (mm); each stream blends
 * up to two reference rainfall stations by ratio. Supports the coordinate -> debris-stream ->
 * warning-light chain. Populated by the SWCB collector (step ③).
 */
@Entity
@Table(name = "debris_stream")
@Getter @Setter @NoArgsConstructor
public class DebrisStream {

    @Id
    @Column(name = "debris_no", nullable = false, length = 20)
    private String debrisNo;

    @Column(length = 20)
    private String county;

    @Column(length = 20)
    private String town;

    @Column(length = 30)
    private String village;

    @Column(name = "alert_value", precision = 7, scale = 2)
    private BigDecimal alertValue;

    @Column(name = "ref_station1", length = 40)
    private String refStation1;

    @Column(name = "ref_ratio1", precision = 6, scale = 3)
    private BigDecimal refRatio1;

    @Column(name = "ref_station2", length = 40)
    private String refStation2;

    @Column(name = "ref_ratio2", precision = 6, scale = 3)
    private BigDecimal refRatio2;

    @Column(name = "updated_at", updatable = false, insertable = false)
    private OffsetDateTime updatedAt;
}
