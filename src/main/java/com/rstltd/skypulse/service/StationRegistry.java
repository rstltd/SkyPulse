package com.rstltd.skypulse.service;

import com.rstltd.skypulse.domain.station.Station;
import com.rstltd.skypulse.domain.station.StationCapability;
import com.rstltd.skypulse.domain.station.StationCapabilityId;
import com.rstltd.skypulse.repository.StationCapabilityRepository;
import com.rstltd.skypulse.repository.StationRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Upserts a station's spatial dimension row and records a capability it reports. A code seen by
 * several collectors accumulates capabilities (RAINFALL / WEATHER / WATER_LEVEL) instead of one
 * collector's type overwriting another's — this is the structural fix for the old type-split.
 *
 * <p>Note: at 10-minute rainfall sampling this is called ~1300x per tick; step 4 adds an
 * in-memory seen-set cache so unchanged stations skip the round trip.
 */
@Service
public class StationRegistry {

    private final StationRepository stationRepo;
    private final StationCapabilityRepository capabilityRepo;

    public StationRegistry(StationRepository stationRepo, StationCapabilityRepository capabilityRepo) {
        this.stationRepo = stationRepo;
        this.capabilityRepo = capabilityRepo;
    }

    @Transactional
    public void register(String code, String name, String source,
                         BigDecimal latitude, BigDecimal longitude, BigDecimal altitude,
                         String county, String township,
                         String capability, String datasetId) {
        if (code == null || code.isBlank()) return;

        Station station = stationRepo.findById(code).orElseGet(Station::new);
        if (station.getStationCode() == null) {
            station.setStationCode(code);
            station.setStationName(name != null ? name : code);
            station.setSource(source);
            station.setIsActive(true);
        }
        // Only overwrite with values we actually have, so a later collector can complete an
        // earlier partial row (e.g. the rainfall feed has coordinates the weather feed lacked).
        if (latitude != null) station.setLatitude(latitude);
        if (longitude != null) station.setLongitude(longitude);
        if (altitude != null) station.setAltitude(altitude);
        if (county != null) station.setCounty(county);
        if (township != null) station.setTownship(township);
        stationRepo.save(station);

        StationCapability cap = capabilityRepo.findById(new StationCapabilityId(code, capability))
                .orElseGet(() -> {
                    StationCapability c = new StationCapability();
                    c.setStationCode(code);
                    c.setCapability(capability);
                    return c;
                });
        if (datasetId != null) cap.setDatasetId(datasetId);
        cap.setLastSeen(TimeUtils.nowUtc());
        capabilityRepo.save(cap);
    }
}
