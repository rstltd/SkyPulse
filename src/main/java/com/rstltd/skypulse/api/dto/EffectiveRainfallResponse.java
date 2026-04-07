package com.rstltd.skypulse.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record EffectiveRainfallResponse(
        String stationCode,
        OffsetDateTime timestamp,
        BigDecimal effectiveRainfall,
        BigDecimal currentIntensity,
        BigDecimal halfLifeHours,
        int windowHours,
        BigDecimal eventTotalRainfall,
        OffsetDateTime eventStartTime,
        BigDecimal eventDurationHours,
        List<TimeSeriesPoint> timeSeries
) {
    public record TimeSeriesPoint(
            OffsetDateTime time,
            BigDecimal intensity,
            BigDecimal effectiveAccumulated,
            BigDecimal eventAccumulated
    ) {}
}
