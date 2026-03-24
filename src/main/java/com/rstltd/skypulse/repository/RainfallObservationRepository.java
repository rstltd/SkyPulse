package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.weather.RainfallObservation;
import com.rstltd.skypulse.domain.weather.RainfallObservationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface RainfallObservationRepository extends JpaRepository<RainfallObservation, RainfallObservationId> {
    List<RainfallObservation> findByStationCodeAndTimeBetween(String stationCode, OffsetDateTime start, OffsetDateTime end);
    List<RainfallObservation> findByTimeBetween(OffsetDateTime start, OffsetDateTime end);
}
