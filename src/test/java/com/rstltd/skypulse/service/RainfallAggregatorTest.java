package com.rstltd.skypulse.service;

import com.rstltd.skypulse.api.dto.context.RainfallContext;
import com.rstltd.skypulse.domain.weather.RainfallObservation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RainfallAggregatorTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 3, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    /** One observation every 10 minutes carrying {@code mm}, for {@code count} steps back from NOW. */
    private static List<RainfallObservation> series(double mm, int count) {
        List<RainfallObservation> obs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RainfallObservation o = new RainfallObservation();
            o.setTime(NOW.minusMinutes(10L * i));
            o.setStationCode("C0X");
            o.setRain10minMm(BigDecimal.valueOf(mm));
            obs.add(o);
        }
        return obs;
    }

    @Test
    void accumulate_sumsTenMinuteBaseIntoRollingWindows() {
        // 2 mm every 10 min = 12 mm/h. Oracle: hN = 12 * N mm.
        List<RainfallObservation> obs = series(2.0, 72 * 6); // 72 h of data
        RainfallContext.AccumulatedMm acc = RainfallAggregator.accumulate(obs, NOW);

        assertEquals(0, new BigDecimal("36.0").compareTo(acc.h3()), "3h = 12*3");
        assertEquals(0, new BigDecimal("72.0").compareTo(acc.h6()), "6h = 12*6");
        assertEquals(0, new BigDecimal("144.0").compareTo(acc.h12()));
        assertEquals(0, new BigDecimal("288.0").compareTo(acc.h24()));
        assertEquals(0, new BigDecimal("576.0").compareTo(acc.h48()));
        assertEquals(0, new BigDecimal("864.0").compareTo(acc.h72()));
    }

    @Test
    void accumulate_windowsAreBounded_dataOutsideWindowExcluded() {
        // Only the 3 most recent 10-min steps carry rain (last 30 min), rest are dry.
        List<RainfallObservation> obs = new ArrayList<>(series(0.0, 72 * 6));
        for (int i = 0; i < 3; i++) obs.get(i).setRain10minMm(new BigDecimal("5.0"));
        RainfallContext.AccumulatedMm acc = RainfallAggregator.accumulate(obs, NOW);
        assertEquals(0, new BigDecimal("15.0").compareTo(acc.h3()));
        assertEquals(0, new BigDecimal("15.0").compareTo(acc.h72()), "same total in every window");
    }

    @Test
    void maxHourlyIntensity_isThePeakRollingHour() {
        // Dry, except one hour (6 steps) of 4 mm/10min = 24 mm/h somewhere in the middle.
        List<RainfallObservation> obs = new ArrayList<>(series(0.0, 72 * 6));
        for (int i = 30; i < 36; i++) obs.get(i).setRain10minMm(new BigDecimal("4.0"));
        assertEquals(0, new BigDecimal("24.0").compareTo(RainfallAggregator.maxHourlyIntensity(obs)));
    }

    @Test
    void nullRain_treatedAsZero_andEmptyIsZero() {
        List<RainfallObservation> obs = series(0.0, 6);
        obs.get(0).setRain10minMm(null);
        assertEquals(0, BigDecimal.ZERO.compareTo(RainfallAggregator.accumulate(obs, NOW).h3()));
        assertEquals(0, BigDecimal.ZERO.compareTo(RainfallAggregator.maxHourlyIntensity(List.of())));
        assertNull(RainfallAggregator.latestTime(List.of()));
    }
}
