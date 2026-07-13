package com.rstltd.skypulse.service;

import com.rstltd.skypulse.service.SwcbEffectiveRainfall.DailyRain;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SwcbEffectiveRainfallTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 8);

    private static DailyRain day(int month, int dom, String mm) {
        return new DailyRain(LocalDate.of(2026, month, dom), new BigDecimal(mm));
    }

    @Test
    void effectiveRainfall_appliesDailyDecay() {
        // Rt = 100*0.7^0 + 100*0.7^1 + 100*0.7^2 = 100 + 70 + 49 = 219.
        var days = List.of(day(3, 8, "100"), day(3, 7, "100"), day(3, 6, "100"));
        assertEquals(0, new BigDecimal("219.00").compareTo(
                SwcbEffectiveRainfall.effectiveRainfall(days, TODAY)));
    }

    @Test
    void effectiveRainfall_excludesOutOfWindowAndFutureDays() {
        var days = List.of(
                day(3, 8, "100"),  // offset 0 -> weight 1
                day(3, 1, "500"),  // offset 7 -> excluded (window is 0..6)
                day(3, 9, "500")); // future -> excluded
        assertEquals(0, new BigDecimal("100.00").compareTo(
                SwcbEffectiveRainfall.effectiveRainfall(days, TODAY)));
    }

    @Test
    void effectiveRainfall_boundaryDaySixIncluded() {
        // 6 days ago is the last included day: weight 0.7^6 = 0.117649.
        var days = List.of(day(3, 2, "1000")); // 2026-03-02 is 6 days before 03-08
        assertEquals(0, new BigDecimal("117.65").compareTo(
                SwcbEffectiveRainfall.effectiveRainfall(days, TODAY)));
    }

    @Test
    void effectiveRainfall_nullDayAndEmptyAreZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                SwcbEffectiveRainfall.effectiveRainfall(List.of(new DailyRain(TODAY, null)), TODAY)));
        assertEquals(0, BigDecimal.ZERO.compareTo(
                SwcbEffectiveRainfall.effectiveRainfall(List.of(), TODAY)));
    }

    @Test
    void signal_boundariesGreenYellowRed() {
        BigDecimal r70 = new BigDecimal("250");
        // yellow at >= 0.8 * 250 = 200; red at >= 250.
        assertEquals("GREEN", SwcbEffectiveRainfall.signal(new BigDecimal("150"), r70, 0.8));
        assertEquals("YELLOW", SwcbEffectiveRainfall.signal(new BigDecimal("200"), r70, 0.8));
        assertEquals("YELLOW", SwcbEffectiveRainfall.signal(new BigDecimal("248.7"), r70, 0.8));
        assertEquals("RED", SwcbEffectiveRainfall.signal(new BigDecimal("250"), r70, 0.8));
        assertEquals("RED", SwcbEffectiveRainfall.signal(new BigDecimal("300"), r70, 0.8));
    }

    @Test
    void signal_nullOrZeroBaselineIsNull() {
        assertNull(SwcbEffectiveRainfall.signal(null, new BigDecimal("250"), 0.8));
        assertNull(SwcbEffectiveRainfall.signal(new BigDecimal("100"), null, 0.8));
        assertNull(SwcbEffectiveRainfall.signal(new BigDecimal("100"), BigDecimal.ZERO, 0.8));
    }
}
