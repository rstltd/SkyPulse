package com.rstltd.skypulse.service;

import com.rstltd.skypulse.domain.hydrology.ReservoirStatus;
import com.rstltd.skypulse.domain.hydrology.WaterLevelObservation;
import com.rstltd.skypulse.repository.ReservoirStatusRepository;
import com.rstltd.skypulse.repository.WaterLevelObservationRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class HydrologyService {

    private final WaterLevelObservationRepository waterLevelRepo;
    private final ReservoirStatusRepository reservoirRepo;

    public HydrologyService(WaterLevelObservationRepository waterLevelRepo,
                            ReservoirStatusRepository reservoirRepo) {
        this.waterLevelRepo = waterLevelRepo;
        this.reservoirRepo = reservoirRepo;
    }

    public List<WaterLevelObservation> getLatestWaterLevels() {
        OffsetDateTime now = TimeUtils.nowUtc();
        return waterLevelRepo.findByTimeBetween(now.minusHours(2), now);
    }

    public List<WaterLevelObservation> getWaterLevelByStation(String stationCode) {
        OffsetDateTime now = TimeUtils.nowUtc();
        return waterLevelRepo.findByStationCodeAndTimeBetween(
                stationCode, now.minusHours(24), now);
    }

    public List<ReservoirStatus> getLatestReservoirStatus() {
        OffsetDateTime now = TimeUtils.nowUtc();
        return reservoirRepo.findByTimeBetween(now.minusHours(2), now);
    }
}
