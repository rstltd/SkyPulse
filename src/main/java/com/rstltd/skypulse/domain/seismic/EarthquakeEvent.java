package com.rstltd.skypulse.domain.seismic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "earthquake_events")
@IdClass(EarthquakeEventId.class)
@Getter @Setter @NoArgsConstructor
public class EarthquakeEvent {

    @Id
    @Column(nullable = false)
    private OffsetDateTime time;

    @Id
    @Column(name = "event_id", nullable = false, length = 50)
    private String eventId;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal magnitude;

    @Column(name = "depth_km", precision = 7, scale = 2)
    private BigDecimal depthKm;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "location_desc", length = 200)
    private String locationDesc;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "max_intensity", length = 10)
    private String maxIntensity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private String rawData;

    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;
}
