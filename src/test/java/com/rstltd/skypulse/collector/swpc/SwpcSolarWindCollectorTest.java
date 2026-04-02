package com.rstltd.skypulse.collector.swpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorResult;
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
        String speedJson = """
                [{"proton_speed":458,"time_tag":"2026-04-02T03:09:00"}]
                """;
        String magJson = """
                [{"bt":13,"bz_gsm":9,"time_tag":"2026-04-02T03:09:00"}]
                """;

        when(swpcApiClient.getRawJson(contains("solar-wind-speed"))).thenReturn(Mono.just(speedJson));
        when(swpcApiClient.getRawJson(contains("solar-wind-mag-field"))).thenReturn(Mono.just(magJson));
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
        String speedJson = """
                [{"proton_speed":500,"time_tag":"2026-04-02T05:00:00"}]
                """;
        String magJson = """
                [{"bt":3,"bz_gsm":-1,"time_tag":"2026-04-02T05:00:00"}]
                """;

        when(swpcApiClient.getRawJson(contains("solar-wind-speed"))).thenReturn(Mono.just(speedJson));
        when(swpcApiClient.getRawJson(contains("solar-wind-mag-field"))).thenReturn(Mono.just(magJson));
        when(solarWindRepo.existsById(any())).thenReturn(true);

        CollectorResult result = collector.collect();

        assertEquals(0, result.persistedCount());
    }
}
