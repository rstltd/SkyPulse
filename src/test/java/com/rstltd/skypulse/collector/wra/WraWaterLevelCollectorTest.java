package com.rstltd.skypulse.collector.wra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.repository.WaterLevelObservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WraWaterLevelCollectorTest {

    @Mock WraApiClient wraApiClient;
    @Mock WaterLevelObservationRepository waterLevelRepo;

    WraWaterLevelCollector collector;

    @BeforeEach
    void setUp() {
        collector = new WraWaterLevelCollector(wraApiClient, waterLevelRepo, new ObjectMapper());
        ReflectionTestUtils.setField(collector, "waterLevelGuid", "test-guid");
    }

    @Test
    void collect_happyPath() {
        String json = """
                [{"stationid":"1010H006","datetime":"2026-03-24T13:10:00","waterlevel":"1.84"},
                 {"stationid":"1010H007","datetime":"2026-03-24T13:20:00","waterlevel":"0.5"}]
                """;
        when(wraApiClient.getDataset("test-guid")).thenReturn(Mono.just(json));
        when(waterLevelRepo.findByTimeBetween(any(), any())).thenReturn(Collections.emptyList());
        when(waterLevelRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(2, result.persistedCount());
    }

    @Test
    void collect_filtersEmptyWaterLevel() {
        String json = """
                [{"stationid":"1010H006","datetime":"2026-03-24T13:10:00","waterlevel":"1.84"},
                 {"stationid":"1010H007","datetime":"2026-03-24T13:20:00","waterlevel":""}]
                """;
        when(wraApiClient.getDataset(anyString())).thenReturn(Mono.just(json));
        when(waterLevelRepo.findByTimeBetween(any(), any())).thenReturn(Collections.emptyList());
        when(waterLevelRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(1, result.validCount());
    }

    @Test
    void collect_emptyArray() {
        when(wraApiClient.getDataset(anyString())).thenReturn(Mono.just("[]"));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.EMPTY, result.status());
    }
}
