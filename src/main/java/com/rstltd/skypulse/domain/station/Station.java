package com.rstltd.skypulse.domain.station;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "stations")
@Getter @Setter @NoArgsConstructor
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_code", nullable = false, unique = true, length = 30)
    private String stationCode;

    @Column(name = "station_name", nullable = false, length = 100)
    private String stationName;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "station_type", nullable = false, length = 30)
    private String stationType;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(precision = 7, scale = 2)
    private BigDecimal altitude;

    @Column(length = 20)
    private String county;

    @Column(length = 20)
    private String township;

    @Column(name = "alert_level1", precision = 8, scale = 3)
    private BigDecimal alertLevel1;

    @Column(name = "alert_level2", precision = 8, scale = 3)
    private BigDecimal alertLevel2;

    @Column(name = "alert_level3", precision = 8, scale = 3)
    private BigDecimal alertLevel3;

    @Column(name = "is_active")
    private Boolean isActive;

    @JsonIgnore
    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    @JsonIgnore
    @Column(name = "updated_at", insertable = false)
    private OffsetDateTime updatedAt;
}
