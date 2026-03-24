package com.rstltd.skypulse.collector.swpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.swpc.dto.SwpcSolarWindSummary;
import com.rstltd.skypulse.repository.SolarWindRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SwpcSolarWindCollectorTest {

    @Mock SwpcApiClient swpcApiClient;
    @Mock SolarWindRecordRepository solarWindRepo;

    SwpcSolarWindCollector collector;

    @BeforeEach
    void setUp() {
        collector = new SwpcSolarWindCollector(swpcApiClient, solarWindRepo, new ObjectMapper());
    }

    @Test
    void collect_mergesSpeedAndMagField() {
        var speed = new SwpcSolarWindSummary("2026-03-24 05:24:00.000", "607", null, null);
        var mag = new SwpcSolarWindSummary("2026-03-24 05:26:00.000", null, "4", "-2");

        when(swpcApiClient.get(contains("solar-wind-speed"), eq(SwpcSolarWindSummary.class)))
                .thenReturn(Mono.just(speed));
        when(swpcApiClient.get(contains("solar-wind-mag-field"), eq(SwpcSolarWindSummary.class)))
                .thenReturn(Mono.just(mag));
        when(solarWindRepo.existsById(any())).thenReturn(false);
        when(solarWindRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(1, result.persistedCount());
        verify(solarWindRepo).save(argThat(r ->
                r.getWindSpeed() != null && r.getBt() != null && r.getBz() != null));
    }

    @Test
    void collect_skipsDuplicate() {
        var speed = new SwpcSolarWindSummary("2026-03-24 05:00:00.000", "500", null, null);
        var mag = new SwpcSolarWindSummary("2026-03-24 05:00:00.000", null, "3", "-1");

        when(swpcApiClient.get(contains("solar-wind-speed"), any())).thenReturn(Mono.just(speed));
        when(swpcApiClient.get(contains("solar-wind-mag-field"), any())).thenReturn(Mono.just(mag));
        when(solarWindRepo.existsById(any())).thenReturn(true);

        CollectorResult result = collector.collect();

        assertEquals(0, result.persistedCount());
    }
}
