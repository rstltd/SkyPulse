package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.hydrology.ReservoirStatus;
import com.rstltd.skypulse.domain.hydrology.ReservoirStatusId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface ReservoirStatusRepository extends JpaRepository<ReservoirStatus, ReservoirStatusId> {
    List<ReservoirStatus> findByReservoirIdAndTimeBetween(String reservoirId, OffsetDateTime start, OffsetDateTime end);
    List<ReservoirStatus> findByTimeBetween(OffsetDateTime start, OffsetDateTime end);
}
