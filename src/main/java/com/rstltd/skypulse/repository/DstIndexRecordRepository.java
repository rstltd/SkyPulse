package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.spaceweather.DstIndexRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface DstIndexRecordRepository extends JpaRepository<DstIndexRecord, OffsetDateTime> {
    Optional<DstIndexRecord> findTopByOrderByTimeDesc();
    List<DstIndexRecord> findByTimeBetweenOrderByTimeDesc(OffsetDateTime start, OffsetDateTime end);
    Page<DstIndexRecord> findByTimeBetweenOrderByTimeDesc(OffsetDateTime start, OffsetDateTime end, Pageable pageable);
}
