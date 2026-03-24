package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.spaceweather.KpIndexRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface KpIndexRecordRepository extends JpaRepository<KpIndexRecord, OffsetDateTime> {
    Optional<KpIndexRecord> findTopByOrderByTimeDesc();
    List<KpIndexRecord> findByTimeBetweenOrderByTimeDesc(OffsetDateTime start, OffsetDateTime end);
    Page<KpIndexRecord> findByTimeBetweenOrderByTimeDesc(OffsetDateTime start, OffsetDateTime end, Pageable pageable);
}
