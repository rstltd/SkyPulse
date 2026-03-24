package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.hydrology.WaterLevelObservation;
import com.rstltd.skypulse.domain.hydrology.WaterLevelObservationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface WaterLevelObservationRepository extends JpaRepository<WaterLevelObservation, WaterLevelObservationId> {
    List<WaterLevelObservation> findByStationCodeAndTimeBetween(String stationCode, OffsetDateTime start, OffsetDateTime end);
    List<WaterLevelObservation> findByTimeBetween(OffsetDateTime start, OffsetDateTime end);
}
