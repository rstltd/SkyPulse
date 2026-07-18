package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.weather.WeatherForecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface WeatherForecastRepository extends JpaRepository<WeatherForecast, Long> {
    List<WeatherForecast> findByLocationNameAndForecastTimeAfter(String locationName, OffsetDateTime after);
    List<WeatherForecast> findByIssuedTimeAfter(OffsetDateTime after);

    /** Lightweight (id, location_name, forecast_time) rows for upsert keying — avoids loading raw_data. */
    @Query("SELECT f.id, f.locationName, f.forecastTime FROM WeatherForecast f")
    List<Object[]> findAllForecastSlots();
}
