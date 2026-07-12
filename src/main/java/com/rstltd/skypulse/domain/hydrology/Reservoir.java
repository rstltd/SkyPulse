package com.rstltd.skypulse.domain.hydrology;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Static reservoir dimension: name, capacity, and coordinates. Name / full level /
 * design capacity are refreshed from the WRA daily reference dataset; coordinates
 * (WGS84) are seeded separately from the Ministry of Environment GISEPA_P_27 dataset.
 * Time-series measurements live in {@link ReservoirStatus}.
 */
@Entity
@Table(name = "reservoirs")
@Getter @Setter @NoArgsConstructor
public class Reservoir {

    @Id
    @Column(name = "reservoir_id", nullable = false, length = 20)
    private String reservoirId;

    @Column(name = "reservoir_name", length = 50)
    private String reservoirName;

    @Column(name = "full_level_m", precision = 8, scale = 3)
    private BigDecimal fullLevelM;

    @Column(name = "design_capacity_m3", precision = 14, scale = 2)
    private BigDecimal designCapacityM3;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(length = 50)
    private String basin;

    @Column(length = 20)
    private String county;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "updated_at", updatable = false, insertable = false)
    private OffsetDateTime updatedAt;
}
