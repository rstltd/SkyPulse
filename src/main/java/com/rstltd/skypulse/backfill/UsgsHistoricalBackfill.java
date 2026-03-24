package com.rstltd.skypulse.backfill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.usgs.UsgsApiClient;
import com.rstltd.skypulse.collector.usgs.dto.GeoJsonResponse;
import com.rstltd.skypulse.domain.seismic.EarthquakeEvent;
import com.rstltd.skypulse.repository.EarthquakeEventRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Component
public class UsgsHistoricalBackfill {

    private static final Logger log = LoggerFactory.getLogger(UsgsHistoricalBackfill.class);

    private final UsgsApiClient usgsApiClient;
    private final EarthquakeEventRepository earthquakeRepo;
    private final ObjectMapper objectMapper;

    @Value("${skypulse.usgs.earthquake.min-magnitude}")
    private double minMagnitude;
    @Value("${skypulse.usgs.earthquake.min-latitude}")
    private double minLatitude;
    @Value("${skypulse.usgs.earthquake.max-latitude}")
    private double maxLatitude;
    @Value("${skypulse.usgs.earthquake.min-longitude}")
    private double minLongitude;
    @Value("${skypulse.usgs.earthquake.max-longitude}")
    private double maxLongitude;

    public UsgsHistoricalBackfill(UsgsApiClient usgsApiClient,
                                   EarthquakeEventRepository earthquakeRepo,
                                   ObjectMapper objectMapper) {
        this.usgsApiClient = usgsApiClient;
        this.earthquakeRepo = earthquakeRepo;
        this.objectMapper = objectMapper;
    }

    public BackfillResult execute(LocalDate startDate, LocalDate endDate) {
        long start = System.currentTimeMillis();
        int totalFetched = 0;
        int totalInserted = 0;
        int totalSkipped = 0;

        try {
            // Process month by month
            LocalDate current = startDate.withDayOfMonth(1);
            while (!current.isAfter(endDate)) {
                LocalDate monthEnd = current.plusMonths(1).minusDays(1);
                if (monthEnd.isAfter(endDate)) monthEnd = endDate;

                log.info("[USGS_BACKFILL] Processing {} to {}", current, monthEnd);

                List<GeoJsonResponse.Feature> features = fetchMonth(current, monthEnd);
                totalFetched += features.size();

                for (var feature : features) {
                    String eventId = "USGS-" + feature.id();
                    if (earthquakeRepo.existsByEventId(eventId)) {
                        totalSkipped++;
                        continue;
                    }
                    EarthquakeEvent entity = mapToEntity(feature, eventId);
                    earthquakeRepo.save(entity);
                    totalInserted++;
                }
                if (totalInserted > 0) earthquakeRepo.flush();

                log.info("[USGS_BACKFILL] Month {} done: {} fetched, {} inserted",
                        current.getMonth(), features.size(), totalInserted);

                current = current.plusMonths(1);
            }

            long duration = System.currentTimeMillis() - start;
            return BackfillResult.success("usgs-earthquake",
                    startDate.toString(), endDate.toString(),
                    totalFetched, totalInserted, totalSkipped, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[USGS_BACKFILL] Failed: {}", e.getMessage());
            return BackfillResult.error("usgs-earthquake",
                    startDate.toString(), endDate.toString(), duration, e.getMessage());
        }
    }

    private List<GeoJsonResponse.Feature> fetchMonth(LocalDate start, LocalDate end) {
        GeoJsonResponse response = usgsApiClient.queryEarthquakes(
                GeoJsonResponse.class,
                minLatitude, maxLatitude, minLongitude, maxLongitude, minMagnitude,
                start, end
        ).block(Duration.ofSeconds(60));

        if (response == null || response.features() == null) {
            return Collections.emptyList();
        }
        return response.features();
    }

    private EarthquakeEvent mapToEntity(GeoJsonResponse.Feature feature, String eventId) {
        var props = feature.properties();
        var coords = feature.geometry().coordinates();

        EarthquakeEvent entity = new EarthquakeEvent();
        entity.setTime(TimeUtils.toUtcOffset(TimeUtils.fromEpochMillis(props.time())));
        entity.setEventId(eventId);
        entity.setMagnitude(BigDecimal.valueOf(props.mag()));
        if (coords.length >= 3) {
            entity.setDepthKm(BigDecimal.valueOf(coords[2]));
        }
        entity.setLatitude(BigDecimal.valueOf(coords[1]));
        entity.setLongitude(BigDecimal.valueOf(coords[0]));
        entity.setLocationDesc(props.place());
        entity.setSource("USGS");
        try {
            entity.setRawData(objectMapper.writeValueAsString(feature));
        } catch (JsonProcessingException ignored) {}
        return entity;
    }
}
