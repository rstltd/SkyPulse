package com.rstltd.skypulse.collector.wra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.repository.ReservoirStatusRepository;
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
class WraReservoirCollectorTest {

    @Mock WraApiClient wraApiClient;
    @Mock ReservoirStatusRepository reservoirRepo;

    WraReservoirCollector collector;

    @BeforeEach
    void setUp() {
        collector = new WraReservoirCollector(wraApiClient, reservoirRepo, new ObjectMapper());
        ReflectionTestUtils.setField(collector, "reservoirGuid", "test-guid");
    }

    @Test
    void collect_happyPath() {
        String json = """
                [{"reservoiridentifier":"50213","observationtime":"2026-03-24T07:00:00",
                  "waterlevel":"11.44","effectivewaterstoragecapacity":"0.0",
                  "inflowdischarge":"","totaloutflow":"","accumulaterainfallincatchment":""}]
                """;
        when(wraApiClient.getDataset("test-guid")).thenReturn(Mono.just(json));
        when(reservoirRepo.findByTimeBetween(any(), any())).thenReturn(Collections.emptyList());
        when(reservoirRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(1, result.persistedCount());
    }

    @Test
    void collect_handlesEmptyStringFields() {
        String json = """
                [{"reservoiridentifier":"50213","observationtime":"2026-03-24T07:00:00",
                  "waterlevel":"","effectivewaterstoragecapacity":"",
                  "inflowdischarge":"","totaloutflow":"","accumulaterainfallincatchment":""}]
                """;
        when(wraApiClient.getDataset(anyString())).thenReturn(Mono.just(json));
        when(reservoirRepo.findByTimeBetween(any(), any())).thenReturn(Collections.emptyList());
        when(reservoirRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        // Should still persist (only reservoirId and time are required)
        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(1, result.persistedCount());
    }

    @Test
    void collect_emptyArray() {
        when(wraApiClient.getDataset(anyString())).thenReturn(Mono.just("[]"));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.EMPTY, result.status());
    }
}
