package com.rstltd.skypulse.service;

import com.rstltd.skypulse.domain.weather.RainfallObservation;
import com.rstltd.skypulse.repository.RainfallObservationRepository;
import com.rstltd.skypulse.repository.WeatherForecastRepository;
import com.rstltd.skypulse.repository.WeatherObservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    @Mock RainfallObservationRepository rainfallRepo;
    @Mock WeatherObservationRepository weatherRepo;
    @Mock WeatherForecastRepository forecastRepo;

    WeatherService service;

    @BeforeEach
    void setUp() {
        service = new WeatherService(rainfallRepo, weatherRepo, forecastRepo);
    }

    /**
     * Oracle is independent of the implementation: accumulated rainfall over a window is,
     * by definition, the sum of the per-hour rainfall in that window. This test fails on the
     * previous implementation (which summed the daily-cumulative "Now" value) and passes on
     * the corrected one — 2.0 + 2.5 + 2.5 = 7.0, not 10.0 + 12.5 + 15.0 = 37.5.
     */
    @Test
    void getAccumulatedRainfall_sumsHourlyRainfall_notDailyCumulative() {
        List<RainfallObservation> obs = List.of(
                rain("2026-03-01T10:00:00Z", "C0X", "10.0", "2.0"),
                rain("2026-03-01T11:00:00Z", "C0X", "12.5", "2.5"),
                rain("2026-03-01T12:00:00Z", "C0X", "15.0", "2.5"));
        when(rainfallRepo.findByStationCodeAndTimeBetween(eq("C0X"), any(), any())).thenReturn(obs);

        BigDecimal result = service.getAccumulatedRainfall("C0X", 3);

        assertEquals(0, new BigDecimal("7.0").compareTo(result),
                "accumulated rainfall must sum the hourly increments (precip_1hr), not the daily cumulative");
    }

    @Test
    void getAccumulatedRainfall_treatsMissingHourlyValueAsZero_andStaysNonNegative() {
        List<RainfallObservation> obs = List.of(
                rain("2026-03-01T10:00:00Z", "C0X", "5.0", null),
                rain("2026-03-01T11:00:00Z", "C0X", "5.0", "3.0"));
        when(rainfallRepo.findByStationCodeAndTimeBetween(eq("C0X"), any(), any())).thenReturn(obs);

        BigDecimal result = service.getAccumulatedRainfall("C0X", 3);

        assertEquals(0, new BigDecimal("3.0").compareTo(result), "a null hourly value contributes nothing");
        assertTrue(result.signum() >= 0, "accumulated rainfall is never negative");
    }

    @Test
    void getAccumulatedRainfall_noObservations_isZero() {
        when(rainfallRepo.findByStationCodeAndTimeBetween(any(), any(), any())).thenReturn(List.of());

        assertEquals(0, BigDecimal.ZERO.compareTo(service.getAccumulatedRainfall("C0X", 24)));
    }

    private static RainfallObservation rain(String time, String code, String precipitation, String precip1hr) {
        RainfallObservation o = new RainfallObservation();
        o.setTime(OffsetDateTime.parse(time));
        o.setStationCode(code);
        if (precipitation != null) o.setPrecipitation(new BigDecimal(precipitation));
        if (precip1hr != null) o.setPrecip1hr(new BigDecimal(precip1hr));
        return o;
    }
}
