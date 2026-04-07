package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.log.SystemLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {

    @Query("SELECT l FROM SystemLog l WHERE l.time BETWEEN :start AND :end " +
           "AND (:category IS NULL OR l.category = :category) " +
           "AND (:level IS NULL OR l.level = :level) " +
           "AND (:source IS NULL OR l.source = :source) " +
           "AND (:keyword IS NULL OR LOWER(l.message) LIKE :keyword) " +
           "ORDER BY l.time DESC")
    Page<SystemLog> findFiltered(@Param("start") OffsetDateTime start,
                                 @Param("end") OffsetDateTime end,
                                 @Param("category") String category,
                                 @Param("level") String level,
                                 @Param("source") String source,
                                 @Param("keyword") String keyword,
                                 Pageable pageable);

    @Query("SELECT l.level, COUNT(l) FROM SystemLog l " +
           "WHERE l.time BETWEEN :start AND :end GROUP BY l.level")
    List<Object[]> countByLevelBetween(@Param("start") OffsetDateTime start,
                                       @Param("end") OffsetDateTime end);

    @Query("SELECT l.source, COUNT(l) FROM SystemLog l " +
           "WHERE l.time BETWEEN :start AND :end GROUP BY l.source")
    List<Object[]> countBySourceBetween(@Param("start") OffsetDateTime start,
                                        @Param("end") OffsetDateTime end);

    @Query(value = "SELECT time_bucket('1 hour', time) AS bucket, level, COUNT(*) " +
                   "FROM system_logs WHERE time BETWEEN :start AND :end " +
                   "GROUP BY bucket, level ORDER BY bucket",
           nativeQuery = true)
    List<Object[]> countByHourAndLevel(@Param("start") OffsetDateTime start,
                                       @Param("end") OffsetDateTime end);

    @Query("SELECT DISTINCT l.source FROM SystemLog l ORDER BY l.source")
    List<String> findDistinctSources();

    @Modifying
    @Query("DELETE FROM SystemLog l WHERE l.time < :before")
    int deleteByTimeBefore(@Param("before") OffsetDateTime before);
}
