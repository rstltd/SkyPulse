package com.rstltd.skypulse.collector.cwa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.cwa.dto.CwaEarthquakeResponse;
import com.rstltd.skypulse.domain.seismic.EarthquakeEvent;
import com.rstltd.skypulse.repository.EarthquakeEventRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Component
public class CwaEarthquakeCollector extends CollectorBase<CwaEarthquakeResponse.Earthquake> {

    private final CwaApiClient cwaApiClient;
    private final EarthquakeEventRepository earthquakeRepo;
    private final ObjectMapper objectMapper;

    @Value("${skypulse.cwa.earthquake.min-magnitude}")
    private double minMagnitude;

    public CwaEarthquakeCollector(CwaApiClient cwaApiClient,
                                  EarthquakeEventRepository earthquakeRepo,
                                  ObjectMapper objectMapper) {
        this.cwaApiClient = cwaApiClient;
        this.earthquakeRepo = earthquakeRepo;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${skypulse.cwa.schedule.earthquake}")
    public CollectorResult collect() {
        return execute();
    }

    @Override
    protected String getSourceName() {
        return "CWA_EARTHQUAKE";
    }

    @Override
    protected Mono<List<CwaEarthquakeResponse.Earthquake>> fetch() {
        return cwaApiClient.getDataset("E-A0015-001", CwaEarthquakeResponse.class)
                .map(response -> {
                    if (response.result() != null && response.result().records() != null
                            && response.result().records().Earthquake() != null) {
                        return response.result().records().Earthquake();
                    }
                    return Collections.<CwaEarthquakeResponse.Earthquake>emptyList();
                });
    }

    @Override
    protected boolean validate(CwaEarthquakeResponse.Earthquake item) {
        if (item.EarthquakeInfo() == null || item.EarthquakeInfo().EarthquakeMagnitude() == null) {
            return false;
        }
        return item.EarthquakeInfo().EarthquakeMagnitude().MagnitudeValue() >= minMagnitude
                && item.EarthquakeInfo().OriginTime() != null
                && item.EarthquakeNo() > 0;
    }

    @Override
    protected int persist(List<CwaEarthquakeResponse.Earthquake> data) {
        int count = 0;
        for (var eq : data) {
            String eventId = "CWA-" + eq.EarthquakeNo();
            if (earthquakeRepo.existsByEventId(eventId)) {
                continue;
            }
            EarthquakeEvent entity = mapToEntity(eq, eventId);
            earthquakeRepo.save(entity);
            count++;
        }
        if (count > 0) earthquakeRepo.flush();
        return count;
    }

    private EarthquakeEvent mapToEntity(CwaEarthquakeResponse.Earthquake eq, String eventId) {
        var info = eq.EarthquakeInfo();
        EarthquakeEvent entity = new EarthquakeEvent();
        entity.setTime(TimeUtils.toUtcOffset(
                TimeUtils.parseCwaTimestamp(info.OriginTime())));
        entity.setEventId(eventId);
        entity.setMagnitude(BigDecimal.valueOf(info.EarthquakeMagnitude().MagnitudeValue()));
        entity.setDepthKm(BigDecimal.valueOf(info.FocalDepth()));
        entity.setLatitude(BigDecimal.valueOf(info.Epicenter().EpicenterLatitude()));
        entity.setLongitude(BigDecimal.valueOf(info.Epicenter().EpicenterLongitude()));
        entity.setLocationDesc(info.Epicenter().Location());
        entity.setSource("CWA");
        try {
            entity.setRawData(objectMapper.writeValueAsString(eq));
        } catch (JsonProcessingException e) {
            log.warn("[CWA_EARTHQUAKE] Failed to serialize raw data for event {}", eventId);
        }
        return entity;
    }
}
