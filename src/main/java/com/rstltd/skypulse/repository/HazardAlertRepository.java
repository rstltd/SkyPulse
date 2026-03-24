package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.alert.HazardAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface HazardAlertRepository extends JpaRepository<HazardAlert, Long> {
    List<HazardAlert> findByAlertType(String alertType);
    List<HazardAlert> findByAlertTimeAfterOrderByAlertTimeDesc(OffsetDateTime after);
    Page<HazardAlert> findByAlertTimeAfterOrderByAlertTimeDesc(OffsetDateTime after, Pageable pageable);
    List<HazardAlert> findByAlertTypeAndAlertTimeBetween(String alertType, OffsetDateTime start, OffsetDateTime end);
    Page<HazardAlert> findByAlertTypeAndAlertTimeBetween(String alertType, OffsetDateTime start, OffsetDateTime end, Pageable pageable);
    Optional<HazardAlert> findBySourceAlertId(String sourceAlertId);
}
