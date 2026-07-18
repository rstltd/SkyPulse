package com.rstltd.skypulse.api.dto.context;

import java.math.BigDecimal;

/**
 * Water-level context from the nearest water-level station: current level plus the station's
 * three alert thresholds and the resulting status (NORMAL / LEVEL1 / LEVEL2 / LEVEL3).
 */
public record WaterLevelContext(
        Provenance provenance,
        Freshness freshness,
        BigDecimal waterLevelM,
        AlertLevels alertLevels,
        String alertStatus
) {
    public record AlertLevels(BigDecimal level1, BigDecimal level2, BigDecimal level3) {}
}
