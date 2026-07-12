package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.station.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StationRepository extends JpaRepository<Station, Long> {
    Optional<Station> findByStationCode(String stationCode);
    List<Station> findBySource(String source);
    List<Station> findByStationType(String stationType);
    List<Station> findBySourceAndIsActiveTrue(String source);
    boolean existsByStationCode(String stationCode);
    List<Station> findByStationCodeIn(Collection<String> stationCodes);

    /**
     * Stations that have reported rainfall since the given time. Rainfall stations
     * (CWA O-A0002-001) whose code was first registered by the weather collector get
     * typed WEATHER, so filtering `stations` by station_type = 'RAINFALL' misses ~half
     * of them; query by actual rainfall data instead. (Proper multi-role station
     * modelling is deferred to the Phase 2 schema rework.)
     */
    @Query("SELECT s FROM Station s WHERE s.stationCode IN " +
           "(SELECT DISTINCT r.stationCode FROM RainfallObservation r WHERE r.time > :since)")
    List<Station> findStationsWithRainfallSince(@Param("since") OffsetDateTime since);
}
