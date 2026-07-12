package com.rstltd.skypulse.repository;

import com.rstltd.skypulse.domain.seismic.EarthquakeStationIntensity;
import com.rstltd.skypulse.domain.seismic.EarthquakeStationIntensityId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EarthquakeStationIntensityRepository
        extends JpaRepository<EarthquakeStationIntensity, EarthquakeStationIntensityId> {
    List<EarthquakeStationIntensity> findByEventId(String eventId);
}
