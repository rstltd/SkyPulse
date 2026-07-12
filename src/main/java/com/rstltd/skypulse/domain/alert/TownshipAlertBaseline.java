package com.rstltd.skypulse.domain.alert;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * SWCB township-level debris-flow alert baseline (from GetCountyTownAlertValueList) — the
 * simpler lookup used first in the coordinate -> township -> warning-light chain.
 * {@code alertValue} is the R70 effective-rainfall threshold (mm). Keyed by (county, town).
 */
@Entity
@Table(name = "township_alert_baseline")
@IdClass(TownshipAlertBaselineId.class)
@Getter @Setter @NoArgsConstructor
public class TownshipAlertBaseline {

    @Id
    @Column(nullable = false, length = 20)
    private String county;

    @Id
    @Column(nullable = false, length = 20)
    private String town;

    @Column(name = "alert_value", precision = 7, scale = 2)
    private BigDecimal alertValue;

    @Column(name = "updated_at", updatable = false, insertable = false)
    private OffsetDateTime updatedAt;
}
