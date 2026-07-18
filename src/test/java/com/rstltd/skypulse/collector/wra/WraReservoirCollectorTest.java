package com.rstltd.skypulse.collector.wra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.domain.hydrology.Reservoir;
import com.rstltd.skypulse.domain.hydrology.ReservoirStatus;
import com.rstltd.skypulse.repository.ReservoirRepository;
import com.rstltd.skypulse.repository.ReservoirStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WraReservoirCollectorTest {

    @Mock WraApiClient wraApiClient;
    @Mock ReservoirStatusRepository reservoirRepo;
    @Mock ReservoirRepository reservoirDimRepo;

    WraReservoirCollector collector;

    static final String REALTIME_GUID = "test-guid";
    static final String DAILY_GUID = "test-daily-guid";

    static final String DAILY_JSON = """
            [{"reservoiridentifier":"50213","reservoirname":"石門水庫",
              "nwlmax":"245.000","capacity":"20930.00"},
             {"reservoiridentifier":"10501","reservoirname":"永和山水庫",
              "nwlmax":"85.0","capacity":"2993.43"}]
            """;

    @BeforeEach
    void setUp() {
        collector = new WraReservoirCollector(wraApiClient, reservoirRepo, reservoirDimRepo, new ObjectMapper());
        ReflectionTestUtils.setField(collector, "reservoirGuid", REALTIME_GUID);
        ReflectionTestUtils.setField(collector, "reservoirDailyGuid", DAILY_GUID);
    }

    @Test
    void collect_happyPath() {
        String json = """
                [{"reservoiridentifier":"50213","observationtime":"2026-03-24T07:00:00",
                  "waterlevel":"11.44","effectivewaterstoragecapacity":"0.0",
                  "inflowdischarge":"","totaloutflow":"","accumulaterainfallincatchment":""}]
                """;
        when(wraApiClient.getDataset(REALTIME_GUID)).thenReturn(Mono.just(json));
        when(wraApiClient.getDataset(DAILY_GUID)).thenReturn(Mono.just(DAILY_JSON));
        when(reservoirRepo.findByTimeBetween(any(), any())).thenReturn(Collections.emptyList());
        when(reservoirRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(1, result.persistedCount());
    }

    @Test
    void collect_enrichesWithRefData() {
        String json = """
                [{"reservoiridentifier":"50213","observationtime":"2026-03-24T07:00:00",
                  "waterlevel":"200.500","effectivewaterstoragecapacity":"10465.00",
                  "inflowdischarge":"5.0","totaloutflow":"3.0","accumulaterainfallincatchment":"12.5"}]
                """;
        when(wraApiClient.getDataset(REALTIME_GUID)).thenReturn(Mono.just(json));
        when(wraApiClient.getDataset(DAILY_GUID)).thenReturn(Mono.just(DAILY_JSON));
        when(reservoirRepo.findByTimeBetween(any(), any())).thenReturn(Collections.emptyList());
        when(reservoirRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        collector.collect();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReservoirStatus>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservoirRepo).saveAll(captor.capture());
        ReservoirStatus saved = captor.getValue().get(0);

        assertEquals(0, new BigDecimal("10465.00").compareTo(saved.getEffectiveStorageM3()));
        // storagePct = 10465.00 / 20930.00 * 100 = 50.00
        assertEquals(0, new BigDecimal("50.00").compareTo(saved.getStoragePct()));

        // Static name / full level are persisted to the reservoirs dimension, not the status row.
        ArgumentCaptor<Reservoir> dimCaptor = ArgumentCaptor.forClass(Reservoir.class);
        verify(reservoirDimRepo, atLeastOnce()).save(dimCaptor.capture());
        Reservoir dim = dimCaptor.getAllValues().stream()
                .filter(d -> "50213".equals(d.getReservoirId())).findFirst().orElseThrow();
        assertEquals("石門水庫", dim.getReservoirName());
        assertEquals(0, new BigDecimal("245.000").compareTo(dim.getFullLevelM()));
    }

    @Test
    void collect_handlesEmptyStringFields() {
        String json = """
                [{"reservoiridentifier":"50213","observationtime":"2026-03-24T07:00:00",
                  "waterlevel":"","effectivewaterstoragecapacity":"",
                  "inflowdischarge":"","totaloutflow":"","accumulaterainfallincatchment":""}]
                """;
        when(wraApiClient.getDataset(REALTIME_GUID)).thenReturn(Mono.just(json));
        when(wraApiClient.getDataset(DAILY_GUID)).thenReturn(Mono.just(DAILY_JSON));
        when(reservoirRepo.findByTimeBetween(any(), any())).thenReturn(Collections.emptyList());
        when(reservoirRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(1, result.persistedCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReservoirStatus>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservoirRepo).saveAll(captor.capture());
        ReservoirStatus saved = captor.getValue().get(0);

        assertNull(saved.getStoragePct(), "storagePct should be null when storage capacity is empty");
    }

    @Test
    void collect_emptyArray() {
        when(wraApiClient.getDataset(REALTIME_GUID)).thenReturn(Mono.just("[]"));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.EMPTY, result.status());
    }

    @Test
    void collect_refDataLoadFailure_skipsAll() {
        String json = """
                [{"reservoiridentifier":"50213","observationtime":"2026-03-24T07:00:00",
                  "waterlevel":"11.44","effectivewaterstoragecapacity":"0.0",
                  "inflowdischarge":"","totaloutflow":"","accumulaterainfallincatchment":""}]
                """;
        when(wraApiClient.getDataset(REALTIME_GUID)).thenReturn(Mono.just(json));
        when(wraApiClient.getDataset(DAILY_GUID)).thenReturn(Mono.error(new RuntimeException("API down")));

        CollectorResult result = collector.collect();

        // Without reference data, all records are filtered out
        assertEquals(CollectorResult.Status.EMPTY, result.status());
        assertEquals(0, result.persistedCount());
    }

    @Test
    void collect_unknownReservoirId_filteredOut() {
        String json = """
                [{"reservoiridentifier":"99999","observationtime":"2026-03-24T07:00:00",
                  "waterlevel":"5.0","effectivewaterstoragecapacity":"100.0",
                  "inflowdischarge":"","totaloutflow":"","accumulaterainfallincatchment":""}]
                """;
        when(wraApiClient.getDataset(REALTIME_GUID)).thenReturn(Mono.just(json));
        when(wraApiClient.getDataset(DAILY_GUID)).thenReturn(Mono.just(DAILY_JSON));

        CollectorResult result = collector.collect();

        // Unknown reservoir ID should be filtered out by validate()
        assertEquals(CollectorResult.Status.EMPTY, result.status());
        assertEquals(0, result.persistedCount());
    }

    @Test
    void collect_zeroCapacity_noStoragePct() {
        String dailyJson = """
                [{"reservoiridentifier":"50213","reservoirname":"測試水庫",
                  "nwlmax":"100.0","capacity":"0"}]
                """;
        String json = """
                [{"reservoiridentifier":"50213","observationtime":"2026-03-24T07:00:00",
                  "waterlevel":"50.0","effectivewaterstoragecapacity":"500.0",
                  "inflowdischarge":"","totaloutflow":"","accumulaterainfallincatchment":""}]
                """;
        when(wraApiClient.getDataset(REALTIME_GUID)).thenReturn(Mono.just(json));
        when(wraApiClient.getDataset(DAILY_GUID)).thenReturn(Mono.just(dailyJson));
        when(reservoirRepo.findByTimeBetween(any(), any())).thenReturn(Collections.emptyList());
        when(reservoirRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReservoirStatus>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservoirRepo).saveAll(captor.capture());
        ReservoirStatus saved = captor.getValue().get(0);

        assertNull(saved.getStoragePct(), "storagePct should be null when capacity is zero");
    }
}
