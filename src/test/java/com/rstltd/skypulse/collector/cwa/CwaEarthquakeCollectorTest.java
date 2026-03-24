package com.rstltd.skypulse.collector.cwa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.cwa.dto.CwaEarthquakeResponse;
import com.rstltd.skypulse.collector.cwa.dto.CwaEarthquakeResponse.*;
import com.rstltd.skypulse.repository.EarthquakeEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CwaEarthquakeCollectorTest {

    @Mock CwaApiClient cwaApiClient;
    @Mock EarthquakeEventRepository earthquakeRepo;

    CwaEarthquakeCollector collector;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        collector = new CwaEarthquakeCollector(cwaApiClient, earthquakeRepo, objectMapper);
        ReflectionTestUtils.setField(collector, "minMagnitude", 4.0);
    }

    @Test
    void collect_persistsM4PlusEarthquakes() {
        var response = buildResponse(List.of(
                buildEarthquake(115027, "2026-03-20 21:34:11", 4.6, 31.9, 23.85, 121.61)
        ));
        when(cwaApiClient.getDataset(eq("E-A0015-001"), any())).thenReturn(Mono.just(response));
        when(earthquakeRepo.existsByEventId("CWA-115027")).thenReturn(false);
        when(earthquakeRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(1, result.persistedCount());
        verify(earthquakeRepo).save(argThat(e -> "CWA-115027".equals(e.getEventId())));
    }

    @Test
    void collect_filtersSmallMagnitude() {
        var response = buildResponse(List.of(
                buildEarthquake(100001, "2026-03-20 10:00:00", 3.5, 10.0, 24.0, 121.5)
        ));
        when(cwaApiClient.getDataset(any(), any())).thenReturn(Mono.just(response));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.EMPTY, result.status());
        verify(earthquakeRepo, never()).save(any());
    }

    @Test
    void collect_skipsDuplicateByEventId() {
        var response = buildResponse(List.of(
                buildEarthquake(115027, "2026-03-20 21:34:11", 5.0, 15.0, 23.5, 121.0)
        ));
        when(cwaApiClient.getDataset(any(), any())).thenReturn(Mono.just(response));
        when(earthquakeRepo.existsByEventId("CWA-115027")).thenReturn(true);

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(0, result.persistedCount());
        verify(earthquakeRepo, never()).save(any());
    }

    private CwaEarthquakeResponse buildResponse(List<Earthquake> earthquakes) {
        return new CwaEarthquakeResponse("true",
                new Result("E-A0015-001", new Records("地震報告", earthquakes)));
    }

    private Earthquake buildEarthquake(int no, String time, double mag, double depth,
                                        double lat, double lon) {
        return new Earthquake(no, "地震報告", "綠色", "test content",
                new EarthquakeInfo(time, "中央氣象署", depth,
                        new Epicenter("test location", lat, lon),
                        new Magnitude("芮氏規模", mag)));
    }
}
