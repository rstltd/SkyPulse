package com.rstltd.skypulse.collector.wra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.wra.dto.WraWaterLevelRecord;
import com.rstltd.skypulse.domain.hydrology.WaterLevelObservation;
import com.rstltd.skypulse.repository.WaterLevelObservationRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class WraWaterLevelCollector extends CollectorBase<WraWaterLevelRecord> {

    private final WraApiClient wraApiClient;
    private final WaterLevelObservationRepository waterLevelRepo;
    private final ObjectMapper objectMapper;

    @Value("${skypulse.wra.water-level-guid}")
    private String waterLevelGuid;

    public WraWaterLevelCollector(WraApiClient wraApiClient,
                                  WaterLevelObservationRepository waterLevelRepo,
                                  ObjectMapper objectMapper) {
        this.wraApiClient = wraApiClient;
        this.waterLevelRepo = waterLevelRepo;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${skypulse.wra.schedule.water-level}")
    public CollectorResult collect() {
        return execute();
    }

    @Override
    protected String getSourceName() {
        return "WRA_WATER_LEVEL";
    }

    @Override
    protected Mono<List<WraWaterLevelRecord>> fetch() {
        return wraApiClient.getDataset(waterLevelGuid)
                .map(json -> {
                    try {
                        return objectMapper.readValue(json,
                                objectMapper.getTypeFactory().constructCollectionType(
                                        List.class, WraWaterLevelRecord.class));
                    } catch (JsonProcessingException e) {
                        log.error("[WRA_WATER_LEVEL] Failed to parse response: {}", e.getMessage());
                        return Collections.<WraWaterLevelRecord>emptyList();
                    }
                });
    }

    @Override
    protected boolean validate(WraWaterLevelRecord item) {
        return item.stationid() != null && !item.stationid().isBlank()
                && item.datetime() != null && !item.datetime().isBlank()
                && parseSafe(item.waterlevel()) != null;
    }

    @Override
    protected int persist(List<WraWaterLevelRecord> data) {
        if (data.isEmpty()) return 0;

        OffsetDateTime batchTime = TimeUtils.toUtcOffset(
                TimeUtils.parseWraTimestamp(data.get(0).datetime()));
        Set<String> existingKeys = waterLevelRepo
                .findByTimeBetween(batchTime.minusMinutes(30), batchTime.plusMinutes(30))
                .stream()
                .map(r -> r.getTime() + "|" + r.getStationCode())
                .collect(Collectors.toSet());

        List<WaterLevelObservation> newObs = data.stream()
                .map(this::mapToEntity)
                .filter(obs -> !existingKeys.contains(obs.getTime() + "|" + obs.getStationCode()))
                .toList();

        if (!newObs.isEmpty()) {
            waterLevelRepo.saveAll(newObs);
            waterLevelRepo.flush();
        }
        return newObs.size();
    }

    private WaterLevelObservation mapToEntity(WraWaterLevelRecord record) {
        WaterLevelObservation obs = new WaterLevelObservation();
        obs.setTime(TimeUtils.toUtcOffset(TimeUtils.parseWraTimestamp(record.datetime())));
        obs.setStationCode(record.stationid());
        obs.setWaterLevel(parseSafe(record.waterlevel()));
        obs.setSource("WRA");
        try {
            obs.setRawData(objectMapper.writeValueAsString(record));
        } catch (JsonProcessingException ignored) {}
        return obs;
    }

    private BigDecimal parseSafe(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
