package com.rstltd.skypulse.collector.usgs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.usgs.dto.GeoJsonResponse;
import com.rstltd.skypulse.collector.usgs.dto.GeoJsonResponse.*;
import com.rstltd.skypulse.repository.EarthquakeEventRepository;
import com.rstltd.skypulse.service.SeismicService;
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
class UsgsEarthquakeCollectorTest {

    @Mock UsgsApiClient usgsApiClient;
    @Mock EarthquakeEventRepository earthquakeRepo;
    @Mock SeismicService seismicService;

    UsgsEarthquakeCollector collector;

    @BeforeEach
    void setUp() {
        collector = new UsgsEarthquakeCollector(usgsApiClient, earthquakeRepo, seismicService, new ObjectMapper());
        ReflectionTestUtils.setField(collector, "minMagnitude", 4.0);
        ReflectionTestUtils.setField(collector, "minLatitude", 21.5);
        ReflectionTestUtils.setField(collector, "maxLatitude", 25.5);
        ReflectionTestUtils.setField(collector, "minLongitude", 119.0);
        ReflectionTestUtils.setField(collector, "maxLongitude", 122.5);
    }

    @Test
    void collect_persistsM4PlusInTaiwan() {
        var response = new GeoJsonResponse("FeatureCollection", List.of(
                new Feature("Feature", "us6000shth",
                        new Properties(4.9, "25 km SSE of Hualien", 1774013561471L, "mb", "earthquake"),
                        new Geometry("Point", new double[]{121.71, 23.77, 29.0}))
        ));
        when(usgsApiClient.queryEarthquakes(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Mono.just(response));
        when(earthquakeRepo.existsByEventId("USGS-us6000shth")).thenReturn(false);
        when(seismicService.isDuplicate(any(), anyDouble(), anyDouble(), anyDouble())).thenReturn(false);
        when(earthquakeRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(1, result.persistedCount());
        verify(earthquakeRepo).save(argThat(e -> "USGS-us6000shth".equals(e.getEventId())));
    }

    @Test
    void collect_filtersOutsideTaiwan() {
        var response = new GeoJsonResponse("FeatureCollection", List.of(
                new Feature("Feature", "outside1",
                        new Properties(5.0, "Tokyo", 1774013561471L, "mb", "earthquake"),
                        new Geometry("Point", new double[]{139.69, 35.68, 10.0}))
        ));
        when(usgsApiClient.queryEarthquakes(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Mono.just(response));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.EMPTY, result.status());
    }

    @Test
    void collect_skipsDuplicateByEventId() {
        var response = new GeoJsonResponse("FeatureCollection", List.of(
                new Feature("Feature", "us6000shth",
                        new Properties(4.9, "Hualien", 1774013561471L, "mb", "earthquake"),
                        new Geometry("Point", new double[]{121.71, 23.77, 29.0}))
        ));
        when(usgsApiClient.queryEarthquakes(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Mono.just(response));
        when(earthquakeRepo.existsByEventId("USGS-us6000shth")).thenReturn(true);

        CollectorResult result = collector.collect();

        assertEquals(0, result.persistedCount());
        verify(earthquakeRepo, never()).save(any());
    }

    @Test
    void collect_emptyFeatures() {
        var response = new GeoJsonResponse("FeatureCollection", Collections.emptyList());
        when(usgsApiClient.queryEarthquakes(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Mono.just(response));

        CollectorResult result = collector.collect();

        assertEquals(CollectorResult.Status.EMPTY, result.status());
    }
}
