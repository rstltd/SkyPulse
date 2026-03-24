package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.spaceweather.SolarWindRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface SolarWindRecordRepository extends JpaRepository<SolarWindRecord, OffsetDateTime> {
    Optional<SolarWindRecord> findTopByOrderByTimeDesc();
    List<SolarWindRecord> findByTimeBetweenOrderByTimeDesc(OffsetDateTime start, OffsetDateTime end);
}
