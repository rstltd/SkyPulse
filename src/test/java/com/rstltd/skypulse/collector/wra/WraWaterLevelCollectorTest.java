package com.rstltd.skypulse.collector.wra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.domain.station.WaterLevelStation;
import com.rstltd.skypulse.repository.WaterLevelObservationRepository;
import com.rstltd.skypulse.repository.WaterLevelStationRepository;
import com.rstltd.skypulse.service.StationRegistry;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WraWaterLevelCollectorTest {

    @Mock WraApiClient wraApiClient;
    @Mock WaterLevelObservationRepository waterLevelRepo;
    @Mock StationRegistry stationRegistry;
    @Mock WaterLevelStationRepository waterLevelStationRepo;

    WraWaterLevelCollector collector;

    @BeforeEach
    void setUp() {
        collector = new WraWaterLevelCollector(
                wraApiClient, waterLevelRepo, stationRegistry, waterLevelStationRepo, new ObjectMapper());
        ReflectionTestUtils.setField(collector, "waterLevelGuid", "test-guid");
        ReflectionTestUtils.setField(collector, "stationInfoGuid", "test-station-guid");
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
        when(wraApiClient.getDataset(eq("test-guid"))).thenReturn(Mono.just(json));
        when(waterLevelRepo.findByTimeBetween(any(), any())).thenReturn(Collections.emptyList());
        when(waterLevelRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(1, result.validCount());
    }

    @Test
    void collect_emptyArray() {
        when(wraApiClient.getDataset(eq("test-guid"))).thenReturn(Mono.just("[]"));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.EMPTY, result.status());
    }

    @Test
    void parseCounty_extractsAndNormalizes() {
        assertEquals("新北市", WraWaterLevelCollector.parseCounty("新北市金山區金山里"));
        assertEquals("臺北市", WraWaterLevelCollector.parseCounty("臺北市中山區"));
        assertEquals("南投縣", WraWaterLevelCollector.parseCounty("南投縣竹山鎮"));
        // 台 → 臺
        assertEquals("臺中市", WraWaterLevelCollector.parseCounty("台中市后里區"));
        assertEquals("臺南市", WraWaterLevelCollector.parseCounty("台南市善化區"));
        assertEquals("臺東縣", WraWaterLevelCollector.parseCounty("台東縣卑南鄉"));
        assertEquals("臺北市", WraWaterLevelCollector.parseCounty("台北市中山區"));
        // Typo fix
        assertEquals("苗栗縣", WraWaterLevelCollector.parseCounty("苗粟縣大湖鄉"));
        // Deprecated county
        assertEquals("屏東縣", WraWaterLevelCollector.parseCounty("屏東市內埔鄉"));
        assertEquals("臺中市", WraWaterLevelCollector.parseCounty("台中縣和平鄉"));
        assertNull(WraWaterLevelCollector.parseCounty(null));
        assertNull(WraWaterLevelCollector.parseCounty(""));
    }

    @Test
    void parseTownship_extractsAndNormalizes() {
        assertEquals("金山區", WraWaterLevelCollector.parseTownship("新北市金山區金山里"));
        assertEquals("中山區", WraWaterLevelCollector.parseTownship("臺北市中山區"));
        assertEquals("竹山鎮", WraWaterLevelCollector.parseTownship("南投縣竹山鎮"));
        // Duplicated suffix
        assertEquals("南投市", WraWaterLevelCollector.parseTownship("南投縣南投市市"));
        // Extra whitespace
        assertEquals("潭子區", WraWaterLevelCollector.parseTownship("台中市潭子 區"));
        assertNull(WraWaterLevelCollector.parseTownship(null));
    }

    @Test
    void init_registersWaterLevelStationWithAlertLevels() {
        String stationJson = """
                [{"basinidentifier":"1010H006","observatoryname":"新磺溪橋",
                  "rivername":"磺溪","locationaddress":"新北市金山區金山里",
                  "observationstatus":"現存","alertlevel1":"5.8","alertlevel2":"4.6","alertlevel3":""},
                 {"basinidentifier":"1010H001","observatoryname":"金山",
                  "rivername":"磺溪","locationaddress":"新北市金山區",
                  "observationstatus":"已廢","alertlevel1":"","alertlevel2":"","alertlevel3":""}]
                """;
        when(wraApiClient.getDataset("test-station-guid")).thenReturn(Mono.just(stationJson));
        when(waterLevelStationRepo.findById(anyString())).thenReturn(Optional.empty());
        when(waterLevelStationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        collector.init();

        // Only "現存" stations are processed (1 of 2): registered with WATER_LEVEL capability,
        // with alert thresholds written to water_level_station.
        verify(stationRegistry, times(1)).register(eq("1010H006"), any(), eq("WRA"),
                any(), any(), any(), any(), any(), eq("WATER_LEVEL"), any());
        ArgumentCaptor<WaterLevelStation> captor = ArgumentCaptor.forClass(WaterLevelStation.class);
        verify(waterLevelStationRepo, times(1)).save(captor.capture());
        WaterLevelStation saved = captor.getValue();
        assertEquals(new BigDecimal("5.8"), saved.getAlertLevel1());
        assertEquals(new BigDecimal("4.6"), saved.getAlertLevel2());
        assertNull(saved.getAlertLevel3());
    }

    @Test
    void init_updatesAlertLevelsForExistingWaterLevelStation() {
        String stationJson = """
                [{"basinidentifier":"1010H006","observatoryname":"新磺溪橋",
                  "rivername":"磺溪","locationaddress":"新北市金山區金山里",
                  "observationstatus":"現存","alertlevel1":"5.8","alertlevel2":"4.6","alertlevel3":"3.0"}]
                """;
        WaterLevelStation existing = new WaterLevelStation();
        existing.setStationCode("1010H006");
        when(wraApiClient.getDataset("test-station-guid")).thenReturn(Mono.just(stationJson));
        when(waterLevelStationRepo.findById("1010H006")).thenReturn(Optional.of(existing));
        when(waterLevelStationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        collector.init();

        verify(waterLevelStationRepo, times(1)).save(any());
        assertEquals(new BigDecimal("5.8"), existing.getAlertLevel1());
        assertEquals(new BigDecimal("4.6"), existing.getAlertLevel2());
        assertEquals(new BigDecimal("3.0"), existing.getAlertLevel3());
    }

    @Test
    void init_failureDoesNotBlockCollector() {
        when(wraApiClient.getDataset("test-station-guid"))
                .thenReturn(Mono.error(new RuntimeException("API down")));

        assertDoesNotThrow(() -> collector.init());

        // Collector should still work
        String json = """
                [{"stationid":"1010H006","datetime":"2026-03-24T13:10:00","waterlevel":"1.84"}]
                """;
        when(wraApiClient.getDataset("test-guid")).thenReturn(Mono.just(json));
        when(waterLevelRepo.findByTimeBetween(any(), any())).thenReturn(Collections.emptyList());
        when(waterLevelRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();
        assertEquals(CollectorResult.Status.SUCCESS, result.status());
    }
}
