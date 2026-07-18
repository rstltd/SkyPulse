package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.weather.RainfallObservation;
import com.rstltd.skypulse.domain.weather.RainfallObservationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface RainfallObservationRepository extends JpaRepository<RainfallObservation, RainfallObservationId> {
    List<RainfallObservation> findByStationCodeAndTimeBetween(String stationCode, OffsetDateTime start, OffsetDateTime end);
    List<RainfallObservation> findByTimeBetween(OffsetDateTime start, OffsetDateTime end);

    /**
     * Per-day rainfall (mm) on the Asia/Taipei calendar from the rainfall_daily continuous
     * aggregate, for the SWCB effective-rainfall daily terms. Returns rows of
     * {@code (day java.sql.Date, rain_mm numeric)}.
     */
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT (bucket AT TIME ZONE 'Asia/Taipei')::date AS day, rain_mm
            FROM rainfall_daily
            WHERE station_code = :code AND bucket >= :since
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> findDailyRain(@org.springframework.data.repository.query.Param("code") String code,
                                 @org.springframework.data.repository.query.Param("since") OffsetDateTime since);
}
