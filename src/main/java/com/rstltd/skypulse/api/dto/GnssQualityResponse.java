package com.rstltd.skypulse.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record GnssQualityResponse(
        OffsetDateTime timestamp,
        GnssQualityLevel qualityLevel,
        BigDecimal kpIndex,
        BigDecimal dstIndex,
        BigDecimal bzComponent,
        BigDecimal solarWindSpeed,
        Integer gScale,
        Integer rScale,
        Integer sScale,
        String assessment,
        String recommendation
) {}
