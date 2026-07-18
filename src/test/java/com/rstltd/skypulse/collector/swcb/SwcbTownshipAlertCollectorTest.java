package com.rstltd.skypulse.collector.swcb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.domain.alert.TownshipAlertBaseline;
import com.rstltd.skypulse.repository.TownshipAlertBaselineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SwcbTownshipAlertCollectorTest {

    @Mock SwcbApiClient swcbApiClient;
    @Mock TownshipAlertBaselineRepository townshipRepo;

    SwcbTownshipAlertCollector collector;

    @BeforeEach
    void setUp() {
        collector = new SwcbTownshipAlertCollector(swcbApiClient, townshipRepo, new ObjectMapper());
    }

    @Test
    void collect_reducesDuplicateTownshipToMinThreshold() {
        // 南投縣/信義鄉 appears twice with different thresholds; the safer (min) wins.
        String json = """
                [{"County":"南投縣","Town":"信義鄉","AlertValue":350},
                 {"County":"南投縣","Town":"信義鄉","AlertValue":300},
                 {"County":"新竹縣","Town":"尖石鄉","AlertValue":400}]
                """;
        when(swcbApiClient.getRawJson(anyString())).thenReturn(Mono.just(json));
        when(townshipRepo.findById(any())).thenReturn(Optional.empty());
        when(townshipRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(2, result.persistedCount(), "two distinct townships");

        ArgumentCaptor<TownshipAlertBaseline> captor = ArgumentCaptor.forClass(TownshipAlertBaseline.class);
        verify(townshipRepo, times(2)).save(captor.capture());
        var xinyi = captor.getAllValues().stream()
                .filter(t -> "信義鄉".equals(t.getTown())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("300").compareTo(xinyi.getAlertValue()),
                "duplicate township reduced to the minimum (safest) threshold");
    }

    @Test
    void collect_skipsRowsMissingKeyOrValue() {
        String json = """
                [{"County":"","Town":"信義鄉","AlertValue":300},
                 {"County":"南投縣","Town":"信義鄉","AlertValue":null}]
                """;
        when(swcbApiClient.getRawJson(anyString())).thenReturn(Mono.just(json));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.EMPTY, result.status());
        verify(townshipRepo, never()).save(any());
    }
}
