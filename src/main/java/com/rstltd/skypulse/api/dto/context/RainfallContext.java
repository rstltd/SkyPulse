package com.rstltd.skypulse.api.dto.context;

import java.math.BigDecimal;

/**
 * Rainfall context from the nearest rainfall station: rolling accumulation windows, the SWCB
 * effective accumulated rainfall (Rt), the rainfall-triggering index (RTI = I x Rt), the township
 * debris-flow alert baseline (R70), and the resulting GREEN/YELLOW/RED warning signal.
 */
public record RainfallContext(
        Provenance provenance,
        Freshness freshness,
        AccumulatedMm accumulatedMm,
        BigDecimal maxHourlyIntensityMm,
        BigDecimal effectiveRainfallMm,
        BigDecimal rti,
        AlertBaseline alertBaseline,
        String signal,
        String signalBasis
) {
    /** Rolling accumulated rainfall (mm) over trailing N-hour windows. */
    public record AccumulatedMm(
            BigDecimal h3, BigDecimal h6, BigDecimal h12,
            BigDecimal h24, BigDecimal h48, BigDecimal h72
    ) {}

    /** SWCB debris-flow alert baseline (R70 threshold, mm) for the township. */
    public record AlertBaseline(
            String township,
            BigDecimal thresholdMm,
            String source,
            String dataset,
            String effectiveFrom
    ) {}
}
