package com.rstltd.skypulse.collector.cwa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.cwa.dto.CwaRainfallResponse;
import com.rstltd.skypulse.collector.cwa.dto.CwaRainfallResponse.*;
import com.rstltd.skypulse.domain.weather.RainfallObservation;
import com.rstltd.skypulse.repository.RainfallObservationRepository;
import com.rstltd.skypulse.service.StationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CwaRainfallCollectorTest {

    @Mock CwaApiClient cwaApiClient;
    @Mock RainfallObservationRepository rainfallRepo;
    @Mock StationRegistry stationRegistry;

    CwaRainfallCollector collector;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        collector = new CwaRainfallCollector(cwaApiClient, rainfallRepo, stationRegistry, objectMapper);
    }

    @Test
    void collect_happyPath() {
        var response = buildResponse(List.of(
                buildStation("C0D660", "九份二山", "12.5", "2024-01-15T08:00:00+08:00"),
                buildStation("466940", "基隆", "0.0", "2024-01-15T08:00:00+08:00")
        ));
        when(cwaApiClient.getDataset(eq("O-A0002-001"), eq(CwaRainfallResponse.class)))
                .thenReturn(Mono.just(response));
        when(rainfallRepo.findByTimeBetween(any(), any())).thenReturn(Collections.emptyList());
        when(rainfallRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(2, result.fetchedCount());
        assertEquals(2, result.persistedCount());
        verify(rainfallRepo).saveAll(anyList());
    }

    @Test
    void collect_filtersNegativePrecipitation() {
        var response = buildResponse(List.of(
                buildStation("C0D660", "test", "12.5", "2024-01-15T08:00:00+08:00"),
                buildStation("INVALID", "test", "-998.0", "2024-01-15T08:00:00+08:00")
        ));
        when(cwaApiClient.getDataset(any(), any())).thenReturn(Mono.just(response));
        when(rainfallRepo.findByTimeBetween(any(), any())).thenReturn(Collections.emptyList());
        when(rainfallRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(2, result.fetchedCount());
        assertEquals(1, result.validCount());
    }

    @Test
    void collect_emptyResponse() {
        var response = new CwaRainfallResponse("true",
                new Records(Collections.emptyList()));
        when(cwaApiClient.getDataset(any(), any())).thenReturn(Mono.just(response));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.EMPTY, result.status());
        verify(rainfallRepo, never()).saveAll(anyList());
    }

    @Test
    void collect_registersStationWithRainfallCapability() {
        var response = buildResponse(List.of(
                buildStation("NEW01", "新站", "5.0", "2024-01-15T08:00:00+08:00")
        ));
        when(cwaApiClient.getDataset(any(), any())).thenReturn(Mono.just(response));
        when(rainfallRepo.findByTimeBetween(any(), any())).thenReturn(Collections.emptyList());
        when(rainfallRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        collector.collect();

        // The station is registered with the RAINFALL capability and its dataset id.
        verify(stationRegistry).register(eq("NEW01"), eq("新站"), eq("CWA"),
                any(), any(), any(), eq("台北市"), eq("中正區"), eq("RAINFALL"), eq("O-A0002-001"));
    }

    @Test
    void collect_skipsDuplicates() {
        var response = buildResponse(List.of(
                buildStation("C0D660", "test", "12.5", "2024-01-15T08:00:00+08:00")
        ));
        when(cwaApiClient.getDataset(any(), any())).thenReturn(Mono.just(response));

        // Simulate existing record
        RainfallObservation existing = new RainfallObservation();
        existing.setTime(java.time.OffsetDateTime.parse("2024-01-15T00:00:00Z"));
        existing.setStationCode("C0D660");
        when(rainfallRepo.findByTimeBetween(any(), any())).thenReturn(List.of(existing));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(0, result.persistedCount());
    }

    // --- Helper builders ---

    private CwaRainfallResponse buildResponse(List<Station> stations) {
        return new CwaRainfallResponse("true", new Records(stations));
    }

    private Station buildStation(String id, String name, String precip, String dateTime) {
        return new Station(name, id,
                new ObsTime(dateTime),
                new GeoInfo(
                        List.of(new Coordinate("WGS84", "25.0", "121.5")),
                        "100.0", "台北市", "中正區"),
                new RainfallElement(
                        new PrecipValue(precip),
                        new PrecipValue("0.0"),
                        new PrecipValue("0.0"),
                        new PrecipValue("0.0"),
                        new PrecipValue("0.0"),
                        new PrecipValue("0.0"),
                        new PrecipValue("0.0")));
    }
}
