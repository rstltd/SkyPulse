package com.rstltd.skypulse.backfill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.usgs.UsgsApiClient;
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

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsgsHistoricalBackfillTest {

    @Mock UsgsApiClient usgsApiClient;
    @Mock EarthquakeEventRepository earthquakeRepo;
    @Mock SeismicService seismicService;

    UsgsHistoricalBackfill backfill;

    @BeforeEach
    void setUp() {
        backfill = new UsgsHistoricalBackfill(usgsApiClient, earthquakeRepo, new ObjectMapper(), seismicService);
        ReflectionTestUtils.setField(backfill, "minMagnitude", 4.0);
        ReflectionTestUtils.setField(backfill, "minLatitude", 21.5);
        ReflectionTestUtils.setField(backfill, "maxLatitude", 25.5);
        ReflectionTestUtils.setField(backfill, "minLongitude", 119.0);
        ReflectionTestUtils.setField(backfill, "maxLongitude", 122.5);
    }

    @Test
    void execute_singleMonth_insertsNewEvents() {
        var response = new GeoJsonResponse("FeatureCollection", List.of(
                new Feature("Feature", "us001",
                        new Properties(4.5, "Hualien", 1704067200000L, "mb", "earthquake"),
                        new Geometry("Point", new double[]{121.5, 23.5, 15.0})),
                new Feature("Feature", "us002",
                        new Properties(5.2, "Taitung", 1704153600000L, "mb", "earthquake"),
                        new Geometry("Point", new double[]{121.0, 22.8, 20.0}))
        ));
        when(usgsApiClient.queryEarthquakes(any(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Mono.just(response));
        when(earthquakeRepo.existsByEventId(anyString())).thenReturn(false);
        when(seismicService.isDuplicate(any(), anyDouble(), anyDouble(), anyDouble())).thenReturn(false);
        when(earthquakeRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        BackfillResult result = backfill.execute(
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(2, result.totalFetched());
        assertEquals(2, result.inserted());
        assertEquals(0, result.skipped());
        assertEquals(0, result.errors());
    }

    @Test
    void execute_skipsDuplicates() {
        var response = new GeoJsonResponse("FeatureCollection", List.of(
                new Feature("Feature", "existing1",
                        new Properties(4.5, "test", 1704067200000L, "mb", "earthquake"),
                        new Geometry("Point", new double[]{121.5, 23.5, 10.0}))
        ));
        when(usgsApiClient.queryEarthquakes(any(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Mono.just(response));
        when(earthquakeRepo.existsByEventId("USGS-existing1")).thenReturn(true);

        BackfillResult result = backfill.execute(
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));

        assertEquals(1, result.totalFetched());
        assertEquals(0, result.inserted());
        assertEquals(1, result.skipped());
    }

    @Test
    void execute_multipleMonths() {
        var response = new GeoJsonResponse("FeatureCollection", List.of(
                new Feature("Feature", "us100",
                        new Properties(4.0, "test", 1704067200000L, "mb", "earthquake"),
                        new Geometry("Point", new double[]{121.0, 23.0, 5.0}))
        ));
        when(usgsApiClient.queryEarthquakes(any(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Mono.just(response));
        when(earthquakeRepo.existsByEventId(anyString())).thenReturn(false);
        when(seismicService.isDuplicate(any(), anyDouble(), anyDouble(), anyDouble())).thenReturn(false);
        when(earthquakeRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        BackfillResult result = backfill.execute(
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 3, 31));

        // 3 months = 3 API calls, each returning 1 event
        assertEquals(3, result.totalFetched());
        assertEquals(3, result.inserted());
    }
}
