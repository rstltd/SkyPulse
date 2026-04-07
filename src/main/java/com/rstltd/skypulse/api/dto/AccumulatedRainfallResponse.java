package com.rstltd.skypulse.api.dto;

import java.math.BigDecimal;

public record AccumulatedRainfallResponse(
        String stationCode,
        int hours,
        BigDecimal accumulatedPrecipitation
) {}
