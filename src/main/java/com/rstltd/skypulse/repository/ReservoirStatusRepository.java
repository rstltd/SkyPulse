package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.hydrology.ReservoirStatus;
import com.rstltd.skypulse.domain.hydrology.ReservoirStatusId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface ReservoirStatusRepository extends JpaRepository<ReservoirStatus, ReservoirStatusId> {
    List<ReservoirStatus> findByReservoirIdAndTimeBetween(String reservoirId, OffsetDateTime start, OffsetDateTime end);
    List<ReservoirStatus> findByTimeBetween(OffsetDateTime start, OffsetDateTime end);

    @Query(value = "SELECT DISTINCT ON (reservoir_id) * FROM reservoir_status " +
            "WHERE time BETWEEN :start AND :end " +
            "ORDER BY reservoir_id, time DESC",
            nativeQuery = true)
    List<ReservoirStatus> findLatestPerReservoir(@Param("start") OffsetDateTime start,
                                                  @Param("end") OffsetDateTime end);
}
