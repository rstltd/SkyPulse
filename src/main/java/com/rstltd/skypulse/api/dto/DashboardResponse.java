package com.rstltd.skypulse.api.dto;

import com.rstltd.skypulse.domain.alert.HazardAlert;
import com.rstltd.skypulse.domain.hydrology.ReservoirStatus;
import com.rstltd.skypulse.domain.hydrology.WaterLevelObservation;
import com.rstltd.skypulse.domain.seismic.EarthquakeEvent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record DashboardResponse(
        String siteId,
        OffsetDateTime timestamp,
        RainfallSummary rainfall,
        GnssQualityResponse gnssQuality,
        List<EarthquakeEvent> recentEarthquakes,
        List<HazardAlert> activeAlerts,
        List<WaterLevelObservation> nearbyWaterLevels,
        List<ReservoirStatus> reservoirs
) {
    public record RainfallSummary(
            String stationCode,
            BigDecimal hourly,
            BigDecimal accumulated3h,
            BigDecimal accumulated24h,
            BigDecimal accumulated48h,
            BigDecimal accumulated72h
    ) {}
}
