package com.rstltd.skypulse.api.dto.context;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Space-weather-based GNSS quality assessment. Global (coordinate-independent) and therefore
 * ALWAYS present in a context response. Each index carries its own source/observed-at provenance.
 */
public record GnssQualityContext(
        String qualityLevel,
        BigDecimal kpIndex,
        BigDecimal dstIndex,
        BigDecimal bzComponent,
        BigDecimal solarWindSpeed,
        Integer gScale,
        Integer rScale,
        Integer sScale,
        String assessment,
        String recommendation,
        boolean global,
        List<IndexProvenance> provenance,
        Freshness freshness
) {
    public record IndexProvenance(String index, String source, OffsetDateTime observedAt) {}
}
