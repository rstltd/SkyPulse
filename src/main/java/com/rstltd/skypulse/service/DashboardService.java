package com.rstltd.skypulse.service;

import com.rstltd.skypulse.api.dto.DashboardResponse;
import com.rstltd.skypulse.api.dto.GnssQualityResponse;
import com.rstltd.skypulse.domain.alert.HazardAlert;
import com.rstltd.skypulse.domain.hydrology.ReservoirStatus;
import com.rstltd.skypulse.domain.hydrology.WaterLevelObservation;
import com.rstltd.skypulse.domain.seismic.EarthquakeEvent;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class DashboardService {

    private final WeatherService weatherService;
    private final SeismicService seismicService;
    private final SpaceWeatherService spaceWeatherService;
    private final HydrologyService hydrologyService;
    private final AlertService alertService;

    public DashboardService(WeatherService weatherService,
                            SeismicService seismicService,
                            SpaceWeatherService spaceWeatherService,
                            HydrologyService hydrologyService,
                            AlertService alertService) {
        this.weatherService = weatherService;
        this.seismicService = seismicService;
        this.spaceWeatherService = spaceWeatherService;
        this.hydrologyService = hydrologyService;
        this.alertService = alertService;
    }

    public DashboardResponse getDashboard(String siteId, double lat, double lon,
                                           String stationCode) {
        // Rainfall summary
        DashboardResponse.RainfallSummary rainfall = buildRainfallSummary(stationCode);

        // GNSS quality
        GnssQualityResponse gnssQuality = spaceWeatherService.assessGnssQuality();

        // Nearby earthquakes (within 100km, last 30 days)
        List<EarthquakeEvent> earthquakes = seismicService.getNearbyEvents(lat, lon, 100);

        // Active alerts
        List<HazardAlert> alerts = alertService.getActiveAlerts();

        // Hydrology
        List<WaterLevelObservation> waterLevels = hydrologyService.getLatestWaterLevels();
        List<ReservoirStatus> reservoirs = hydrologyService.getLatestReservoirStatus();

        return new DashboardResponse(
                siteId,
                TimeUtils.nowUtc(),
                rainfall,
                gnssQuality,
                earthquakes,
                alerts,
                waterLevels,
                reservoirs
        );
    }

    private DashboardResponse.RainfallSummary buildRainfallSummary(String stationCode) {
        if (stationCode == null || stationCode.isBlank()) {
            return new DashboardResponse.RainfallSummary(null,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return new DashboardResponse.RainfallSummary(
                stationCode,
                weatherService.getAccumulatedRainfall(stationCode, 1),
                weatherService.getAccumulatedRainfall(stationCode, 3),
                weatherService.getAccumulatedRainfall(stationCode, 24),
                weatherService.getAccumulatedRainfall(stationCode, 48),
                weatherService.getAccumulatedRainfall(stationCode, 72)
        );
    }
}
