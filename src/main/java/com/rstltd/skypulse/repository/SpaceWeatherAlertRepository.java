package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.spaceweather.SpaceWeatherAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface SpaceWeatherAlertRepository extends JpaRepository<SpaceWeatherAlert, Long> {
    List<SpaceWeatherAlert> findByAlertTimeAfterOrderByAlertTimeDesc(OffsetDateTime after);
    Optional<SpaceWeatherAlert> findBySerialNumber(String serialNumber);
}
