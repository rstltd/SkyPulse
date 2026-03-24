package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.weather.WeatherForecast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface WeatherForecastRepository extends JpaRepository<WeatherForecast, Long> {
    List<WeatherForecast> findByLocationNameAndForecastTimeAfter(String locationName, OffsetDateTime after);
    List<WeatherForecast> findByIssuedTimeAfter(OffsetDateTime after);
}
