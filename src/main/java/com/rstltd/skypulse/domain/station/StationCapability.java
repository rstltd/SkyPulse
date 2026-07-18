package com.rstltd.skypulse.domain.station;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * A capability a station reports (RAINFALL / WEATHER / WATER_LEVEL). A single station code can
 * carry several capabilities — this replaces the old single {@code station_type} that let the
 * first collector to see a shared code "claim" the type and hide it from the others.
 */
@Entity
@Table(name = "station_capability")
@IdClass(StationCapabilityId.class)
@Getter @Setter @NoArgsConstructor
public class StationCapability {

    @Id
    @Column(name = "station_code", length = 40)
    private String stationCode;

    @Id
    @Column(length = 20)
    private String capability;

    @Column(name = "dataset_id", length = 60)
    private String datasetId;

    @Column(name = "first_seen", updatable = false, insertable = false)
    private OffsetDateTime firstSeen;

    @Column(name = "last_seen")
    private OffsetDateTime lastSeen;
}
