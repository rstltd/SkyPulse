package com.rstltd.skypulse.collector.usgs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.usgs.dto.GeoJsonResponse;
import com.rstltd.skypulse.domain.seismic.EarthquakeEvent;
import com.rstltd.skypulse.repository.EarthquakeEventRepository;
import com.rstltd.skypulse.service.SeismicService;
import com.rstltd.skypulse.util.GeoUtils;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Component
public class UsgsEarthquakeCollector extends CollectorBase<GeoJsonResponse.Feature> {

    private final UsgsApiClient usgsApiClient;
    private final EarthquakeEventRepository earthquakeRepo;
    private final SeismicService seismicService;
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

    public UsgsEarthquakeCollector(UsgsApiClient usgsApiClient,
                                   EarthquakeEventRepository earthquakeRepo,
                                   SeismicService seismicService,
                                   ObjectMapper objectMapper) {
        this.usgsApiClient = usgsApiClient;
        this.earthquakeRepo = earthquakeRepo;
        this.seismicService = seismicService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${skypulse.usgs.schedule.earthquake}")
    public CollectorResult collect() {
        return execute();
    }

    @Override
    protected String getSourceName() {
        return "USGS_EARTHQUAKE";
    }

    @Override
    protected Mono<List<GeoJsonResponse.Feature>> fetch() {
        return usgsApiClient.queryEarthquakes(
                        GeoJsonResponse.class,
                        minLatitude, maxLatitude, minLongitude, maxLongitude, minMagnitude)
                .map(response -> {
                    if (response.features() != null) return response.features();
                    return Collections.<GeoJsonResponse.Feature>emptyList();
                });
    }

    @Override
    protected boolean validate(GeoJsonResponse.Feature item) {
        if (item.id() == null || item.properties() == null || item.geometry() == null
                || item.geometry().coordinates() == null || item.geometry().coordinates().length < 2) {
            return false;
        }
        double lat = item.geometry().coordinates()[1];
        double lon = item.geometry().coordinates()[0];
        return item.properties().mag() >= minMagnitude
                && GeoUtils.isInTaiwanRegion(lat, lon);
    }

    @Override
    protected int persist(List<GeoJsonResponse.Feature> data) {
        int count = 0;
        for (var feature : data) {
            String eventId = "USGS-" + feature.id();
            if (earthquakeRepo.existsByEventId(eventId)) continue;

            // Cross-source dedup: skip if CWA already has this earthquake
            var props = feature.properties();
            var coords = feature.geometry().coordinates();
            var time = TimeUtils.toUtcOffset(TimeUtils.fromEpochMillis(props.time()));
            if (seismicService.isDuplicate(time, coords[1], coords[0], props.mag())) {
                log.debug("[USGS_EARTHQUAKE] Skipping duplicate (already in CWA): {}", eventId);
                continue;
            }

            EarthquakeEvent entity = mapToEntity(feature, eventId);
            earthquakeRepo.save(entity);
            count++;
        }
        if (count > 0) earthquakeRepo.flush();
        return count;
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
