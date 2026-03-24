package com.rstltd.skypulse.collector.swpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.repository.SpaceWeatherAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SwpcAlertCollectorTest {

    @Mock SwpcApiClient swpcApiClient;
    @Mock SpaceWeatherAlertRepository alertRepo;

    SwpcAlertCollector collector;

    @BeforeEach
    void setUp() {
        collector = new SwpcAlertCollector(swpcApiClient, alertRepo, new ObjectMapper());
    }

    @Test
    void collect_parsesAlertsAndExtractsSerialNumber() {
        String json = """
                [{"product_id":"K05W","issue_datetime":"2026-03-24 04:20:30.370",
                  "message":"Space Weather Message Code: WARK05\\r\\nSerial Number: 2216\\r\\nIssue Time: 2026 Mar 24 0420 UTC"}]
                """;
        when(swpcApiClient.getRawJson(anyString())).thenReturn(Mono.just(json));
        when(alertRepo.findBySerialNumber("2216")).thenReturn(Optional.empty());
        when(alertRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(1, result.persistedCount());
        verify(alertRepo).save(argThat(a ->
                "2216".equals(a.getSerialNumber()) && "K05W".equals(a.getAlertType())));
    }

    @Test
    void collect_skipsDuplicateBySerialNumber() {
        String json = """
                [{"product_id":"K05W","issue_datetime":"2026-03-24 04:20:30.370",
                  "message":"Serial Number: 2216\\r\\ntest"}]
                """;
        when(swpcApiClient.getRawJson(anyString())).thenReturn(Mono.just(json));
        when(alertRepo.findBySerialNumber("2216")).thenReturn(Optional.of(new com.rstltd.skypulse.domain.spaceweather.SpaceWeatherAlert()));

        CollectorResult result = collector.collect();

        assertEquals(0, result.persistedCount());
    }

    @Test
    void collect_emptyAlerts() {
        when(swpcApiClient.getRawJson(anyString())).thenReturn(Mono.just("[]"));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.EMPTY, result.status());
    }
}
