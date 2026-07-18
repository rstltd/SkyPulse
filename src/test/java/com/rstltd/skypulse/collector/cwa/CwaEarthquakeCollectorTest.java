package com.rstltd.skypulse.collector.cwa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.cwa.dto.CwaEarthquakeResponse;
import com.rstltd.skypulse.collector.cwa.dto.CwaEarthquakeResponse.*;
import com.rstltd.skypulse.domain.seismic.EarthquakeStationIntensity;
import com.rstltd.skypulse.repository.EarthquakeEventRepository;
import com.rstltd.skypulse.repository.EarthquakeStationIntensityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CwaEarthquakeCollectorTest {

    @Mock CwaApiClient cwaApiClient;
    @Mock EarthquakeEventRepository earthquakeRepo;
    @Mock EarthquakeStationIntensityRepository stationIntensityRepo;

    CwaEarthquakeCollector collector;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        collector = new CwaEarthquakeCollector(cwaApiClient, earthquakeRepo, stationIntensityRepo, objectMapper);
        ReflectionTestUtils.setField(collector, "minMagnitude", 4.0);
    }

    @Test
    void collect_persistsM4PlusEarthquakes() {
        var response = buildResponse(List.of(
                buildEarthquake(115027, "2026-03-20 21:34:11", 4.6, 31.9, 23.85, 121.61)
        ));
        when(cwaApiClient.getDataset(eq("E-A0015-001"), any())).thenReturn(Mono.just(response));
        when(earthquakeRepo.existsByEventId("CWA-115027")).thenReturn(false);
        when(earthquakeRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(1, result.persistedCount());
        verify(earthquakeRepo).save(argThat(e -> "CWA-115027".equals(e.getEventId())));
    }

    @Test
    void collect_filtersSmallMagnitude() {
        var response = buildResponse(List.of(
                buildEarthquake(100001, "2026-03-20 10:00:00", 3.5, 10.0, 24.0, 121.5)
        ));
        when(cwaApiClient.getDataset(any(), any())).thenReturn(Mono.just(response));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.EMPTY, result.status());
        verify(earthquakeRepo, never()).save(any());
    }

    @Test
    void collect_skipsDuplicateByEventId() {
        var response = buildResponse(List.of(
                buildEarthquake(115027, "2026-03-20 21:34:11", 5.0, 15.0, 23.5, 121.0)
        ));
        when(cwaApiClient.getDataset(any(), any())).thenReturn(Mono.just(response));
        when(earthquakeRepo.existsByEventId("CWA-115027")).thenReturn(true);

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(0, result.persistedCount());
        verify(earthquakeRepo, never()).save(any());
    }

    @Test
    void collect_persistsStationIntensityAndComputesEventMax() {
        // Two stations: 花蓮 5弱 (rank 5), 臺東 4級 (rank 4). Event max = 5弱.
        var intensity = new Intensity(List.of(
                new ShakingArea("最大震度5弱地區", "花蓮縣", "5弱", List.of(
                        new EqStation("西林", "ESL", "5弱", 23.966, 121.493,
                                new PgaPgv("gal", 46.0, 33.9, 19.8, 88.5),
                                new PgaPgv("kine", 1.1, 0.6, 0.3, 6.2)))),
                new ShakingArea("最大震度4級地區", "臺東縣", "4級", List.of(
                        new EqStation("成功", "ECG", "4級", 23.10, 121.37,
                                new PgaPgv("gal", 20.0, 18.0, 9.0, 30.1),
                                new PgaPgv("kine", 0.5, 0.4, 0.2, 2.1))))));
        var eq = new Earthquake(115030, "地震報告", "黃色", "content",
                new EarthquakeInfo("2026-03-21 08:00:00", "中央氣象署", 20.0,
                        new Epicenter("花蓮縣近海", 23.9, 121.6),
                        new Magnitude("芮氏規模", 5.2)),
                intensity);
        when(cwaApiClient.getDataset(any(), any())).thenReturn(Mono.just(buildResponse(List.of(eq))));
        when(earthquakeRepo.existsByEventId("CWA-115030")).thenReturn(false);
        when(earthquakeRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        collector.collect();

        verify(earthquakeRepo).save(argThat(e ->
                "5弱".equals(e.getMaxIntensity()) && e.getMaxIntensityRank() == (short) 5));

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(stationIntensityRepo).saveAll(captor.capture());
        List<EarthquakeStationIntensity> saved = captor.getValue();
        assertEquals(2, saved.size());
        var esl = saved.stream().filter(s -> "ESL".equals(s.getStationCode())).findFirst().orElseThrow();
        assertEquals((short) 5, esl.getIntensityRank());
        assertEquals(0, new java.math.BigDecimal("88.5").compareTo(esl.getPgaGal()));
    }

    private CwaEarthquakeResponse buildResponse(List<Earthquake> earthquakes) {
        return new CwaEarthquakeResponse("true",
                new Records("地震報告", earthquakes));
    }

    private Earthquake buildEarthquake(int no, String time, double mag, double depth,
                                        double lat, double lon) {
        return new Earthquake(no, "地震報告", "綠色", "test content",
                new EarthquakeInfo(time, "中央氣象署", depth,
                        new Epicenter("test location", lat, lon),
                        new Magnitude("芮氏規模", mag)),
                null);
    }
}
