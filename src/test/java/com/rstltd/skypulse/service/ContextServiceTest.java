package com.rstltd.skypulse.service;

import com.rstltd.skypulse.api.dto.GnssQualityLevel;
import com.rstltd.skypulse.api.dto.GnssQualityResponse;
import com.rstltd.skypulse.api.dto.context.ContextResponse;
import com.rstltd.skypulse.domain.station.Station;
import com.rstltd.skypulse.domain.station.WaterLevelStation;
import com.rstltd.skypulse.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContextServiceTest {

    @Mock StationRepository stationRepo;
    @Mock StationCapabilityRepository capabilityRepo;
    @Mock RainfallObservationRepository rainfallRepo;
    @Mock WaterLevelObservationRepository waterLevelRepo;
    @Mock WaterLevelStationRepository waterLevelStationRepo;
    @Mock EarthquakeEventRepository earthquakeRepo;
    @Mock SpaceWeatherService spaceWeatherService;
    @Mock SeismicService seismicService;

    ContextService service;

    @BeforeEach
    void setUp() {
        service = new ContextService(stationRepo, capabilityRepo, rainfallRepo, waterLevelRepo,
                waterLevelStationRepo, earthquakeRepo, spaceWeatherService, seismicService);
        ReflectionTestUtils.setField(service, "maxAutoRadiusKm", 50.0);
        ReflectionTestUtils.setField(service, "defaultQuakeRadiusKm", 100.0);
        ReflectionTestUtils.setField(service, "rainfallSla", 1800L);
        ReflectionTestUtils.setField(service, "waterLevelSla", 1800L);
        ReflectionTestUtils.setField(service, "seismicSla", 900L);
        ReflectionTestUtils.setField(service, "gnssKpSla", 10800L);
        lenient().when(spaceWeatherService.assessGnssQuality()).thenReturn(gnss());
        lenient().when(rainfallRepo.findByStationCodeAndTimeBetween(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        lenient().when(seismicService.getNearbyEvents(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());
    }

    private static GnssQualityResponse gnss() {
        return new GnssQualityResponse(OffsetDateTime.now(ZoneOffset.UTC), GnssQualityLevel.CAUTION,
                new BigDecimal("4.3"), new BigDecimal("-35"), new BigDecimal("-6.2"),
                new BigDecimal("520"), 1, 0, 0, "Minor disturbance.", "MONITOR");
    }

    private static Station station(String code, String county, String township) {
        Station s = new Station();
        s.setStationCode(code);
        s.setStationName(code);
        s.setSource("CWA");
        s.setCounty(county);
        s.setTownship(township);
        s.setLatitude(new BigDecimal("23.5108"));
        s.setLongitude(new BigDecimal("120.8052"));
        return s;
    }

    @Test
    void getContext_autoRadius_buildsBlocksAndLocation() {
        when(stationRepo.findNearestWithCapability(anyDouble(), anyDouble(), eq("RAINFALL"), anyDouble()))
                .thenReturn(Optional.of(station("C0Z100", "嘉義縣", "阿里山鄉")));
        when(stationRepo.findNearestWithCapability(anyDouble(), anyDouble(), eq("WATER_LEVEL"), anyDouble()))
                .thenReturn(Optional.empty());

        ContextResponse r = service.getContext(23.508, 120.805, null, null, null, null, null);

        assertTrue(r.query().autoRadius());
        assertEquals("阿里山鄉", r.location().township());
        assertTrue(r.location().inTaiwan());
        assertNotNull(r.gnssQuality());
        assertTrue(r.gnssQuality().global());
        assertNotNull(r.rainfall(), "rainfall block built from nearest station");
        assertNotNull(r.seismic(), "seismic block always built (count 0 here)");
        assertEquals(0, r.seismic().nearbyCount());
        assertNull(r.waterLevel(), "no water station -> null block");
        assertTrue(r.warnings().stream().anyMatch(w ->
                "NO_STATION_IN_RADIUS".equals(w.code()) && "waterLevel".equals(w.domain())));
        assertTrue(r.meta().partial(), "water block missing -> partial");
    }

    @Test
    void getContext_fixedRadius_waterFallbackLocation() {
        when(stationRepo.findNearestWithCapability(anyDouble(), anyDouble(), eq("RAINFALL"), anyDouble()))
                .thenReturn(Optional.empty());
        when(stationRepo.findNearestWithCapability(anyDouble(), anyDouble(), eq("WATER_LEVEL"), anyDouble()))
                .thenReturn(Optional.of(station("1730H013", "嘉義縣", "番路鄉")));

        ContextResponse r = service.getContext(23.5, 120.8, 15.0, null, null, null, null);

        assertFalse(r.query().autoRadius());
        assertEquals(15.0, r.query().radiusKm());
        assertEquals("番路鄉", r.location().township());
        assertNull(r.rainfall());
        assertNotNull(r.waterLevel());
        assertTrue(r.warnings().stream().anyMatch(w ->
                "NO_STATION_IN_RADIUS".equals(w.code()) && "rainfall".equals(w.domain())));
    }

    @Test
    void getContext_outsideTaiwan_warns() {
        ContextResponse r = service.getContext(35.68, 139.77, null, null, null, null, null);
        assertFalse(r.location().inTaiwan());
        assertTrue(r.warnings().stream().anyMatch(w -> "OUTSIDE_COVERAGE".equals(w.code())));
        assertNotNull(r.gnssQuality());
    }

    @Test
    void alertStatus_thresholds() {
        WaterLevelStation wls = new WaterLevelStation();
        wls.setAlertLevel1(new BigDecimal("84.0"));
        wls.setAlertLevel2(new BigDecimal("85.5"));
        wls.setAlertLevel3(new BigDecimal("87.0"));
        assertEquals("NORMAL", ContextService.alertStatus(new BigDecimal("82.0"), wls));
        assertEquals("LEVEL1", ContextService.alertStatus(new BigDecimal("84.0"), wls));
        assertEquals("LEVEL2", ContextService.alertStatus(new BigDecimal("86.0"), wls));
        assertEquals("LEVEL3", ContextService.alertStatus(new BigDecimal("88.0"), wls));
        assertNull(ContextService.alertStatus(null, wls));
        assertNull(ContextService.alertStatus(new BigDecimal("82.0"), null));
    }
}
