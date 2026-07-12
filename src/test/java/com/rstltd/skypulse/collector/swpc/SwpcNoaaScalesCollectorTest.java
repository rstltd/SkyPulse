package com.rstltd.skypulse.collector.swpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.domain.spaceweather.NoaaScale;
import com.rstltd.skypulse.repository.NoaaScaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SwpcNoaaScalesCollectorTest {

    @Mock SwpcApiClient swpcApiClient;
    @Mock NoaaScaleRepository noaaScaleRepo;

    SwpcNoaaScalesCollector collector;

    // Shape mirrors the live products/noaa-scales.json: "-1" (past, ignored), "0" (observed),
    // "1".."3" (predicted). Predicted R/S report probabilities with Scale=null.
    static final String JSON = """
            {
              "-1": {"DateStamp":"2026-07-11","TimeStamp":"17:00:00",
                     "R":{"Scale":"1","Text":"minor"},"S":{"Scale":"0","Text":"none"},"G":{"Scale":"2","Text":"moderate"}},
              "0":  {"DateStamp":"2026-07-12","TimeStamp":"17:00:00",
                     "R":{"Scale":"0","Text":"none","MinorProb":null},"S":{"Scale":"0","Text":"none","Prob":null},"G":{"Scale":"1","Text":"minor"}},
              "1":  {"DateStamp":"2026-07-12","TimeStamp":"17:00:00",
                     "R":{"Scale":null,"MinorProb":"25"},"S":{"Scale":null,"Prob":"1"},"G":{"Scale":"3","Text":"strong"}},
              "2":  {"DateStamp":"2026-07-13","TimeStamp":"00:00:00",
                     "R":{"Scale":null},"S":{"Scale":null},"G":{"Scale":"0","Text":"none"}},
              "3":  {"DateStamp":"2026-07-14","TimeStamp":"00:00:00",
                     "R":{"Scale":null},"S":{"Scale":null},"G":{"Scale":"0","Text":"none"}}
            }
            """;

    @BeforeEach
    void setUp() {
        collector = new SwpcNoaaScalesCollector(swpcApiClient, noaaScaleRepo, new ObjectMapper());
    }

    @Test
    void collect_mapsHorizonsAndScales_ignoresPastDay() {
        when(swpcApiClient.getRawJson(anyString())).thenReturn(Mono.just(JSON));
        when(noaaScaleRepo.existsById(any())).thenReturn(false);
        when(noaaScaleRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        // "-1" is ignored; "0".."3" persist (all carry at least one scale).
        assertEquals(4, result.persistedCount());

        ArgumentCaptor<NoaaScale> captor = ArgumentCaptor.forClass(NoaaScale.class);
        verify(noaaScaleRepo, times(4)).save(captor.capture());
        List<NoaaScale> saved = captor.getAllValues();

        NoaaScale observed = saved.stream().filter(n -> "observed".equals(n.getHorizon()))
                .findFirst().orElseThrow();
        assertEquals(1, observed.getGScale());
        assertEquals(0, observed.getRScale());
        assertEquals(0, observed.getSScale());
        // All horizons share the observed ("0") entry's issue time.
        assertTrue(saved.stream().allMatch(n -> n.getTime().equals(observed.getTime())));

        NoaaScale d1 = saved.stream().filter(n -> "predicted_d1".equals(n.getHorizon()))
                .findFirst().orElseThrow();
        assertEquals(3, d1.getGScale());
        assertNull(d1.getRScale(), "predicted R scale is null (probability-only)");
    }

    @Test
    void collect_skipsExisting() {
        when(swpcApiClient.getRawJson(anyString())).thenReturn(Mono.just(JSON));
        when(noaaScaleRepo.existsById(any())).thenReturn(true);

        CollectorResult result = collector.collect();

        assertEquals(0, result.persistedCount());
        verify(noaaScaleRepo, never()).save(any());
    }

    @Test
    void collect_emptyDocument_isEmpty() {
        when(swpcApiClient.getRawJson(anyString())).thenReturn(Mono.just("{}"));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.EMPTY, result.status());
        verify(noaaScaleRepo, never()).save(any());
    }
}
