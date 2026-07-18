package com.rstltd.skypulse.service;

import com.rstltd.skypulse.api.dto.context.RainfallContext;
import com.rstltd.skypulse.domain.weather.RainfallObservation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Pure rainfall math over 10-minute observations ({@code rain_10min_mm} is the non-overlapping
 * accumulation base, so windows are plain sums). No I/O — easy to unit-test against an oracle.
 */
public final class RainfallAggregator {

    private static final int[] WINDOW_HOURS = {3, 6, 12, 24, 48, 72};

    private RainfallAggregator() {}

    /** Rolling accumulated rainfall (mm) for the trailing 3/6/12/24/48/72 h ending at {@code now}. */
    public static RainfallContext.AccumulatedMm accumulate(List<RainfallObservation> obs, OffsetDateTime now) {
        BigDecimal[] sums = new BigDecimal[WINDOW_HOURS.length];
        for (int i = 0; i < sums.length; i++) sums[i] = BigDecimal.ZERO;
        for (RainfallObservation o : obs) {
            BigDecimal r = rain(o);
            if (r.signum() == 0) continue;
            long minutesAgo = minutesBetween(o.getTime(), now);
            if (minutesAgo < 0) continue; // ignore future-dated observations
            for (int i = 0; i < WINDOW_HOURS.length; i++) {
                // rain_10min at time t covers (t-10min, t]; a window ending at now covers
                // (now - Nh, now], so include t iff 0 <= (now - t) < Nh.
                if (minutesAgo < WINDOW_HOURS[i] * 60L) sums[i] = sums[i].add(r);
            }
        }
        return new RainfallContext.AccumulatedMm(
                scale(sums[0]), scale(sums[1]), scale(sums[2]),
                scale(sums[3]), scale(sums[4]), scale(sums[5]));
    }

    /**
     * Max rolling 1-hour rainfall (mm) over the observation set — the intensity I used by the SWCB
     * rainfall-triggering index. For each observation the trailing-hour sum ending at its time is
     * computed; the maximum is returned.
     */
    public static BigDecimal maxHourlyIntensity(List<RainfallObservation> obs) {
        BigDecimal max = BigDecimal.ZERO;
        for (RainfallObservation end : obs) {
            OffsetDateTime endTime = end.getTime();
            BigDecimal hour = BigDecimal.ZERO;
            for (RainfallObservation o : obs) {
                long diff = minutesBetween(o.getTime(), endTime);
                if (diff >= 0 && diff < 60) hour = hour.add(rain(o));
            }
            if (hour.compareTo(max) > 0) max = hour;
        }
        return scale(max);
    }

    /** Latest observation time, or null if empty. */
    public static OffsetDateTime latestTime(List<RainfallObservation> obs) {
        OffsetDateTime latest = null;
        for (RainfallObservation o : obs) {
            if (latest == null || o.getTime().isAfter(latest)) latest = o.getTime();
        }
        return latest;
    }

    private static BigDecimal rain(RainfallObservation o) {
        BigDecimal r = o.getRain10minMm();
        return (r == null || r.signum() < 0) ? BigDecimal.ZERO : r;
    }

    private static long minutesBetween(OffsetDateTime from, OffsetDateTime to) {
        return java.time.Duration.between(from, to).toMinutes();
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(1, RoundingMode.HALF_UP);
    }
}
