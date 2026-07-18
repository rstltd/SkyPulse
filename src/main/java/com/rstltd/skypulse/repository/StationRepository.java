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

    /**
     * Nearest active station reporting {@code capability} within {@code radiusM} metres of the
     * query point, or empty if none. Ordering/filtering uses cube+earthdistance
     * (earth_distance(ll_to_earth(...))); the reported distance is recomputed with Haversine in
     * Java for a stable 2-decimal contract value.
     */
    @Query(value = """
            SELECT s.* FROM stations s
            JOIN station_capability c
              ON c.station_code = s.station_code AND c.capability = :capability
            WHERE s.is_active = TRUE AND s.latitude IS NOT NULL AND s.longitude IS NOT NULL
              AND earth_distance(ll_to_earth(s.latitude, s.longitude),
                                 ll_to_earth(:lat, :lon)) <= :radiusM
            ORDER BY earth_distance(ll_to_earth(s.latitude, s.longitude),
                                    ll_to_earth(:lat, :lon))
            LIMIT 1
            """, nativeQuery = true)
    Optional<Station> findNearestWithCapability(@Param("lat") double lat,
                                                @Param("lon") double lon,
                                                @Param("capability") String capability,
                                                @Param("radiusM") double radiusM);
}
