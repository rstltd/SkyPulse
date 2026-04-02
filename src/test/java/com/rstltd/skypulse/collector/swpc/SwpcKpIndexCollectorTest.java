package com.rstltd.skypulse.collector.swpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.repository.KpIndexRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SwpcKpIndexCollectorTest {

    @Mock SwpcApiClient swpcApiClient;
    @Mock KpIndexRecordRepository kpRepo;

    SwpcKpIndexCollector collector;

    @BeforeEach
    void setUp() {
        collector = new SwpcKpIndexCollector(swpcApiClient, kpRepo, new ObjectMapper());
    }

    @Test
    void collect_happyPath_parsesObjectArrayAndPersists() {
        String json = """
                [{"time_tag":"2026-03-20T00:00:00","Kp":2.67,"a_running":12,"station_count":8},
                 {"time_tag":"2026-03-20T03:00:00","Kp":3.50,"a_running":15,"station_count":8}]
                """;
        when(swpcApiClient.getRawJson(anyString())).thenReturn(Mono.just(json));
        when(kpRepo.existsById(any(OffsetDateTime.class))).thenReturn(false);
        when(kpRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(2, result.fetchedCount());
        assertEquals(2, result.persistedCount());
        verify(kpRepo, times(2)).save(any());
    }

    @Test
    void collect_filtersInvalidKpValues() {
        String json = """
                [{"time_tag":"2026-03-20T00:00:00","Kp":3.0},
                 {"time_tag":"2026-03-20T03:00:00","Kp":-1.0}]
                """;
        when(swpcApiClient.getRawJson(anyString())).thenReturn(Mono.just(json));
        when(kpRepo.existsById(any())).thenReturn(false);
        when(kpRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(1, result.validCount());
        assertEquals(1, result.persistedCount());
    }

    @Test
    void collect_skipsDuplicates() {
        String json = """
                [{"time_tag":"2026-03-20T00:00:00","Kp":3.0}]
                """;
        when(swpcApiClient.getRawJson(anyString())).thenReturn(Mono.just(json));
        when(kpRepo.existsById(any())).thenReturn(true);

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(0, result.persistedCount());
        verify(kpRepo, never()).save(any());
    }

    @Test
    void collect_emptyArray() {
        when(swpcApiClient.getRawJson(anyString())).thenReturn(Mono.just("[]"));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.EMPTY, result.status());
    }
}
