package com.rstltd.skypulse.service;

import com.rstltd.skypulse.domain.seismic.EarthquakeEvent;
import com.rstltd.skypulse.repository.EarthquakeEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeismicServiceTest {

    @Mock EarthquakeEventRepository earthquakeRepo;
    SeismicService service;

    private static final OffsetDateTime T1 = OffsetDateTime.of(2025, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new SeismicService(earthquakeRepo);
    }

    @Test
    void deduplicateEvents_removesUsgsWhenCwaExists() {
        var cwa = buildEvent("CWA-115027", T1, 23.500, 121.610, 4.6, "CWA");
        var usgs = buildEvent("USGS-us6000shth", T1.plusSeconds(15), 23.502, 121.612, 4.5, "USGS");

        List<EarthquakeEvent> result = service.deduplicateEvents(List.of(cwa, usgs));

        assertEquals(1, result.size());
        assertEquals("CWA", result.get(0).getSource());
    }

    @Test
    void deduplicateEvents_keepsBothWhenDifferentEarthquakes() {
        var eq1 = buildEvent("CWA-001", T1, 23.500, 121.610, 4.6, "CWA");
        var eq2 = buildEvent("USGS-002", T1.plusHours(2), 24.800, 121.200, 5.1, "USGS");

        List<EarthquakeEvent> result = service.deduplicateEvents(List.of(eq1, eq2));

        assertEquals(2, result.size());
    }

    @Test
    void deduplicateEvents_cwaReplacesOnlyMatchingUsgs_notUnrelatedEvents() {
        var unrelated = buildEvent("USGS-far", T1.plusHours(3), 24.800, 121.200, 5.1, "USGS");
        var usgs = buildEvent("USGS-1", T1, 23.500, 121.610, 4.6, "USGS");
        var cwa = buildEvent("CWA-1", T1.plusSeconds(10), 23.502, 121.612, 4.5, "CWA");

        var result = service.deduplicateEvents(List.of(unrelated, usgs, cwa));

        // CWA replaces the matching USGS event only; an unrelated earthquake must survive.
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(e -> "USGS-far".equals(e.getEventId())),
                "the unrelated earthquake must not be removed by CWA de-duplication");
        assertTrue(result.stream().anyMatch(e -> "CWA".equals(e.getSource())));
    }

    @Test
    void deduplicateEvents_prefersUsgsIfNoCwa() {
        var usgs1 = buildEvent("USGS-001", T1, 23.5, 121.6, 4.5, "USGS");
        var usgs2 = buildEvent("USGS-002", T1.plusHours(1), 23.8, 121.3, 5.0, "USGS");

        List<EarthquakeEvent> result = service.deduplicateEvents(List.of(usgs1, usgs2));

        assertEquals(2, result.size());
    }

    @Test
    void deduplicateEvents_timeDiffOver30Seconds_notDuplicate() {
        var cwa = buildEvent("CWA-001", T1, 23.500, 121.610, 4.6, "CWA");
        var usgs = buildEvent("USGS-001", T1.plusSeconds(60), 23.500, 121.610, 4.6, "USGS");

        List<EarthquakeEvent> result = service.deduplicateEvents(List.of(cwa, usgs));

        assertEquals(2, result.size());
    }

    @Test
    void deduplicateEvents_distanceOver10km_notDuplicate() {
        var cwa = buildEvent("CWA-001", T1, 23.500, 121.610, 4.6, "CWA");
        var usgs = buildEvent("USGS-001", T1.plusSeconds(5), 24.000, 121.610, 4.6, "USGS");

        List<EarthquakeEvent> result = service.deduplicateEvents(List.of(cwa, usgs));

        assertEquals(2, result.size());
    }

    @Test
    void deduplicateEvents_magnitudeDiffOver03_notDuplicate() {
        var cwa = buildEvent("CWA-001", T1, 23.500, 121.610, 4.6, "CWA");
        var usgs = buildEvent("USGS-001", T1.plusSeconds(5), 23.502, 121.612, 5.2, "USGS");

        List<EarthquakeEvent> result = service.deduplicateEvents(List.of(cwa, usgs));

        assertEquals(2, result.size());
    }

    @Test
    void isDuplicate_findsMatch() {
        var existing = buildEvent("CWA-001", T1, 23.500, 121.610, 4.6, "CWA");
        when(earthquakeRepo.findByTimeBetween(any(), any())).thenReturn(List.of(existing));

        assertTrue(service.isDuplicate(T1.plusSeconds(10), 23.502, 121.612, 4.5));
    }

    @Test
    void isDuplicate_noMatch() {
        when(earthquakeRepo.findByTimeBetween(any(), any())).thenReturn(List.of());

        assertFalse(service.isDuplicate(T1, 23.5, 121.6, 4.5));
    }

    @Test
    void isDuplicate_candidateInWindowButFarAway_returnsFalse() {
        // A candidate exists inside the ±30s time window but is ~160km away: not a duplicate.
        var farAway = buildEvent("USGS-x", T1, 25.0, 122.0, 4.6, "USGS");
        when(earthquakeRepo.findByTimeBetween(any(), any())).thenReturn(List.of(farAway));

        assertFalse(service.isDuplicate(T1, 23.5, 121.6, 4.6),
                "a candidate in the time window but outside the distance threshold is not a duplicate");
    }

    private EarthquakeEvent buildEvent(String eventId, OffsetDateTime time,
                                        double lat, double lon, double mag, String source) {
        EarthquakeEvent e = new EarthquakeEvent();
        e.setEventId(eventId);
        e.setTime(time);
        e.setLatitude(BigDecimal.valueOf(lat));
        e.setLongitude(BigDecimal.valueOf(lon));
        e.setMagnitude(BigDecimal.valueOf(mag));
        e.setSource(source);
        return e;
    }
}
