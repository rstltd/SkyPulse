package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.weather.WeatherObservation;
import com.rstltd.skypulse.domain.weather.WeatherObservationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface WeatherObservationRepository extends JpaRepository<WeatherObservation, WeatherObservationId> {
    List<WeatherObservation> findByStationCodeAndTimeBetween(String stationCode, OffsetDateTime start, OffsetDateTime end);
    List<WeatherObservation> findByTimeBetween(OffsetDateTime start, OffsetDateTime end);
}
