package com.rstltd.skypulse.collector.swcb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.domain.alert.DebrisStream;
import com.rstltd.skypulse.repository.DebrisStreamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SwcbDebrisCollectorTest {

    @Mock SwcbApiClient swcbApiClient;
    @Mock DebrisStreamRepository debrisRepo;

    SwcbDebrisCollector collector;

    // Shape mirrors live GetDebrisRainData (extra keys ignored).
    static final String JSON = """
            [{"County":"新北市","Town":"雙溪區","Vill":"牡丹里","DebrisNO":"新北DF183",
              "AlertValue":550,"STID1":"C2A650","STName1":"料角坑","STRT1":172.85,
              "STID2":"C0A640","STName2":"雙溪","STRT2":50.89,"CountyCode":"65000"},
             {"County":"南投縣","Town":"信義鄉","Vill":"神木村","DebrisNO":"南投DF001",
              "AlertValue":250,"STID1":"C0H9A0","STName1":"神木","STRT1":100.0,
              "STID2":null,"STName2":null,"STRT2":null}]
            """;

    @BeforeEach
    void setUp() {
        collector = new SwcbDebrisCollector(swcbApiClient, debrisRepo, new ObjectMapper());
    }

    @Test
    void collect_upsertsExistingAndNew() {
        DebrisStream existing = new DebrisStream();
        existing.setDebrisNo("新北DF183");
        existing.setAlertValue(new BigDecimal("999")); // stale value to be overwritten

        when(swcbApiClient.getRawJson(anyString())).thenReturn(Mono.just(JSON));
        when(debrisRepo.findAll()).thenReturn(List.of(existing));
        when(debrisRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(2, result.persistedCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DebrisStream>> captor = ArgumentCaptor.forClass(List.class);
        verify(debrisRepo).saveAll(captor.capture());
        List<DebrisStream> saved = captor.getValue();

        DebrisStream df183 = saved.stream().filter(d -> "新北DF183".equals(d.getDebrisNo()))
                .findFirst().orElseThrow();
        assertSame(existing, df183, "existing row reused (updated in place)");
        assertEquals(0, new BigDecimal("550").compareTo(df183.getAlertValue()));
        assertEquals("C2A650", df183.getRefStation1());
        assertEquals(0, new BigDecimal("50.89").compareTo(df183.getRefRatio2()));

        DebrisStream df001 = saved.stream().filter(d -> "南投DF001".equals(d.getDebrisNo()))
                .findFirst().orElseThrow();
        assertEquals("神木村", df001.getVillage());
        assertNull(df001.getRefStation2(), "single-reference stream leaves STID2 null");
    }

    @Test
    void collect_skipsRowsMissingKeyOrValue() {
        String json = """
                [{"DebrisNO":"","AlertValue":300},
                 {"DebrisNO":"南投DF002","AlertValue":null}]
                """;
        when(swcbApiClient.getRawJson(anyString())).thenReturn(Mono.just(json));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.EMPTY, result.status());
        verify(debrisRepo, never()).saveAll(anyList());
    }
}
