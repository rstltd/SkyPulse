package com.rstltd.skypulse.service;

import com.rstltd.skypulse.api.dto.DashboardResponse;
import com.rstltd.skypulse.api.dto.GnssQualityLevel;
import com.rstltd.skypulse.api.dto.GnssQualityResponse;
import com.rstltd.skypulse.util.TimeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock WeatherService weatherService;
    @Mock SeismicService seismicService;
    @Mock SpaceWeatherService spaceWeatherService;
    @Mock HydrologyService hydrologyService;
    @Mock AlertService alertService;

    DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                weatherService, seismicService, spaceWeatherService,
                hydrologyService, alertService);
    }

    @Test
    void getDashboard_returnsConsolidatedResponse() {
        when(weatherService.getAccumulatedRainfall(eq("C0D660"), anyInt()))
                .thenReturn(BigDecimal.TEN);
        when(spaceWeatherService.assessGnssQuality()).thenReturn(
                new GnssQualityResponse(TimeUtils.nowUtc(), GnssQualityLevel.NORMAL,
                        new BigDecimal("2.0"), new BigDecimal("-10"), null, null,
                        null, null, null, "Normal", "NORMAL"));
        when(seismicService.getNearbyEvents(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());
        when(alertService.getActiveAlerts()).thenReturn(Collections.emptyList());
        when(hydrologyService.getLatestWaterLevels()).thenReturn(Collections.emptyList());
        when(hydrologyService.getLatestReservoirStatus()).thenReturn(Collections.emptyList());

        DashboardResponse response = dashboardService.getDashboard(
                "GNSS-001", 23.5, 121.0, "C0D660");

        assertNotNull(response);
        assertEquals("GNSS-001", response.siteId());
        assertNotNull(response.timestamp());
        assertNotNull(response.rainfall());
        assertEquals("C0D660", response.rainfall().stationCode());
        assertEquals(GnssQualityLevel.NORMAL, response.gnssQuality().qualityLevel());
        assertNotNull(response.recentEarthquakes());
        assertNotNull(response.activeAlerts());
    }

    @Test
    void getDashboard_withoutStationCode_returnsZeroRainfall() {
        when(spaceWeatherService.assessGnssQuality()).thenReturn(
                new GnssQualityResponse(TimeUtils.nowUtc(), GnssQualityLevel.NORMAL,
                        null, null, null, null, null, null, null, "No data", "NORMAL"));
        when(seismicService.getNearbyEvents(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());
        when(alertService.getActiveAlerts()).thenReturn(Collections.emptyList());
        when(hydrologyService.getLatestWaterLevels()).thenReturn(Collections.emptyList());
        when(hydrologyService.getLatestReservoirStatus()).thenReturn(Collections.emptyList());

        DashboardResponse response = dashboardService.getDashboard(
                "GNSS-002", 24.0, 121.5, null);

        assertEquals(BigDecimal.ZERO, response.rainfall().hourly());
        assertEquals(BigDecimal.ZERO, response.rainfall().accumulated72h());
    }
}
