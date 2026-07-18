package com.rstltd.skypulse.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * SWCB (水土保持署) standard effective accumulated rainfall and the debris-flow warning signal.
 * <p>
 * Rt = R0 + Σ(i=1..6) α^i · Ri, with daily decay α = 0.7 over a 7-day window (R0 = today on the
 * local Asia/Taipei calendar, Ri = rainfall i days ago). The debris-flow alert baseline is the R70
 * effective-rainfall threshold (mm). See docs/PHASE2_DESIGN.md §2.
 */
public final class SwcbEffectiveRainfall {

    /** SWCB daily decay coefficient (locked: passed the 102 debris-flow review, in use since). */
    private static final BigDecimal ALPHA = new BigDecimal("0.7");
    private static final int WINDOW_DAYS = 6; // R0..R6 = 7 calendar days
    public static final String GREEN = "GREEN";
    public static final String YELLOW = "YELLOW";
    public static final String RED = "RED";

    private SwcbEffectiveRainfall() {}

    /** Daily rainfall total (mm) for a local calendar day. */
    public record DailyRain(LocalDate date, BigDecimal rainMm) {}

    /**
     * Effective accumulated rainfall Rt for {@code today}, summing each day's rainfall weighted by
     * α^(days ago). Days outside the 7-day window (or in the future) are ignored.
     */
    public static BigDecimal effectiveRainfall(List<DailyRain> days, LocalDate today) {
        BigDecimal rt = BigDecimal.ZERO;
        for (DailyRain d : days) {
            if (d.rainMm() == null) continue;
            long offset = ChronoUnit.DAYS.between(d.date(), today);
            if (offset < 0 || offset > WINDOW_DAYS) continue;
            BigDecimal weight = ALPHA.pow((int) offset);
            rt = rt.add(d.rainMm().multiply(weight, MathContext.DECIMAL64));
        }
        return rt.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Warning signal from effective rainfall vs the R70 baseline:
     * RED when Rt has reached the threshold, YELLOW when within {@code yellowFraction} of it,
     * else GREEN. Returns null when either value is unknown.
     */
    public static String signal(BigDecimal rt, BigDecimal r70, double yellowFraction) {
        if (rt == null || r70 == null || r70.signum() <= 0) return null;
        if (rt.compareTo(r70) >= 0) return RED;
        if (rt.compareTo(r70.multiply(BigDecimal.valueOf(yellowFraction))) >= 0) return YELLOW;
        return GREEN;
    }
}
