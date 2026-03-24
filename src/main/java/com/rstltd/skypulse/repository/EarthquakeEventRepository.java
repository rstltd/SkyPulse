package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.seismic.EarthquakeEvent;
import com.rstltd.skypulse.domain.seismic.EarthquakeEventId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface EarthquakeEventRepository extends JpaRepository<EarthquakeEvent, EarthquakeEventId> {
    Optional<EarthquakeEvent> findByEventId(String eventId);
    boolean existsByEventId(String eventId);
    List<EarthquakeEvent> findByTimeBetween(OffsetDateTime start, OffsetDateTime end);
    Page<EarthquakeEvent> findByTimeBetween(OffsetDateTime start, OffsetDateTime end, Pageable pageable);
    List<EarthquakeEvent> findByTimeBetweenAndMagnitudeGreaterThanEqual(OffsetDateTime start, OffsetDateTime end, BigDecimal minMagnitude);
    Page<EarthquakeEvent> findByTimeBetweenAndMagnitudeGreaterThanEqual(OffsetDateTime start, OffsetDateTime end, BigDecimal minMagnitude, Pageable pageable);
    List<EarthquakeEvent> findByTimeBetweenAndSourceNot(OffsetDateTime start, OffsetDateTime end, String source);
}
