package com.rstltd.skypulse.service;

import com.rstltd.skypulse.api.dto.GnssQualityLevel;
import com.rstltd.skypulse.api.dto.GnssQualityResponse;
import com.rstltd.skypulse.api.dto.context.ContextResponse;
import com.rstltd.skypulse.domain.station.Station;
import com.rstltd.skypulse.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContextServiceTest {

    @Mock StationRepository stationRepo;
    @Mock SpaceWeatherService spaceWeatherService;

    ContextService service;

    @BeforeEach
    void setUp() {
        service = new ContextService(stationRepo, spaceWeatherService);
        ReflectionTestUtils.setField(service, "maxAutoRadiusKm", 50.0);
        ReflectionTestUtils.setField(service, "defaultQuakeRadiusKm", 100.0);
        lenient().when(spaceWeatherService.assessGnssQuality()).thenReturn(gnss());
    }

    private static GnssQualityResponse gnss() {
        return new GnssQualityResponse(OffsetDateTime.now(ZoneOffset.UTC), GnssQualityLevel.CAUTION,
                new BigDecimal("4.3"), new BigDecimal("-35"), new BigDecimal("-6.2"),
                new BigDecimal("520"), 1, 0, 0, "Minor disturbance.", "MONITOR");
    }

    private static Station station(String code, String county, String township) {
        Station s = new Station();
        s.setStationCode(code);
        s.setCounty(county);
        s.setTownship(township);
        return s;
    }

    @Test
    void getContext_autoRadius_populatesLocationAndGnss() {
        when(stationRepo.findNearestWithCapability(anyDouble(), anyDouble(), eq("RAINFALL"), anyDouble()))
                .thenReturn(Optional.of(station("C0Z100", "嘉義縣", "阿里山鄉")));

        ContextResponse r = service.getContext(23.508, 120.805, null, null, null, null, null);

        assertTrue(r.query().autoRadius(), "radiusKm omitted -> auto");
        assertNull(r.query().radiusKm());
        assertEquals(100.0, r.query().quakeRadiusKm());
        assertEquals("嘉義縣", r.location().county());
        assertEquals("阿里山鄉", r.location().township());
        assertTrue(r.location().inTaiwan());
        assertNotNull(r.gnssQuality(), "gnss quality is always present");
        assertTrue(r.gnssQuality().global());
        assertEquals("CAUTION", r.gnssQuality().qualityLevel());
        assertTrue(r.meta().partial(), "data blocks not yet computed -> partial");
        assertTrue(r.warnings().isEmpty());
    }

    @Test
    void getContext_fixedRadius_isNotAuto_andFallsBackToWaterStationForLocation() {
        when(stationRepo.findNearestWithCapability(anyDouble(), anyDouble(), eq("RAINFALL"), anyDouble()))
                .thenReturn(Optional.empty());
        when(stationRepo.findNearestWithCapability(anyDouble(), anyDouble(), eq("WATER_LEVEL"), anyDouble()))
                .thenReturn(Optional.of(station("1730H013", "嘉義縣", "番路鄉")));

        ContextResponse r = service.getContext(23.5, 120.8, 15.0, null, null, null, null);

        assertFalse(r.query().autoRadius());
        assertEquals(15.0, r.query().radiusKm());
        assertEquals("番路鄉", r.location().township(), "location falls back to nearest water station");
    }

    @Test
    void getContext_outsideTaiwan_warnsAndFlagsLocation() {
        // Tokyo — valid lat/lon but outside the Taiwan bbox.
        ContextResponse r = service.getContext(35.68, 139.77, null, null, null, null, null);

        assertFalse(r.location().inTaiwan());
        assertTrue(r.warnings().stream().anyMatch(w -> "OUTSIDE_COVERAGE".equals(w.code())));
        assertNotNull(r.gnssQuality(), "gnss is global, still returned outside Taiwan");
        // Nearest-station lookups are attempted but return empty for an out-of-area point.
        verify(stationRepo, atLeastOnce()).findNearestWithCapability(anyDouble(), anyDouble(), any(), anyDouble());
    }
}
