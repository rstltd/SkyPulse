package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.station.Station;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StationRepository extends JpaRepository<Station, Long> {
    Optional<Station> findByStationCode(String stationCode);
    List<Station> findBySource(String source);
    List<Station> findByStationType(String stationType);
    List<Station> findBySourceAndIsActiveTrue(String source);
    boolean existsByStationCode(String stationCode);
}
