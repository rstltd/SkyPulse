package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.station.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StationRepository extends JpaRepository<Station, String> {
    Optional<Station> findByStationCode(String stationCode);
    List<Station> findBySource(String source);
    List<Station> findBySourceAndIsActiveTrue(String source);
    boolean existsByStationCode(String stationCode);
    List<Station> findByStationCodeIn(Collection<String> stationCodes);

    /**
     * Stations that report the given capability (RAINFALL / WEATHER / WATER_LEVEL). Because a
     * shared code can carry several capabilities, this returns every station that actually
     * reports that data — unlike the old station_type filter, which showed only ~half.
     */
    @Query("SELECT s FROM Station s WHERE s.stationCode IN " +
           "(SELECT c.stationCode FROM StationCapability c WHERE c.capability = :capability)")
    List<Station> findByCapability(@Param("capability") String capability);
}
