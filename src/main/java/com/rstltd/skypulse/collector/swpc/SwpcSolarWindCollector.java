package com.rstltd.skypulse.collector.swpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.swpc.dto.SwpcSolarWindSummary;
import com.rstltd.skypulse.domain.spaceweather.SolarWindRecord;
import com.rstltd.skypulse.repository.SolarWindRecordRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

@Component
public class SwpcSolarWindCollector extends CollectorBase<SwpcSolarWindSummary> {

    private final SwpcApiClient swpcApiClient;
    private final SolarWindRecordRepository solarWindRepo;
    private final ObjectMapper objectMapper;

    public SwpcSolarWindCollector(SwpcApiClient swpcApiClient,
                                  SolarWindRecordRepository solarWindRepo,
                                  ObjectMapper objectMapper) {
        this.swpcApiClient = swpcApiClient;
        this.solarWindRepo = solarWindRepo;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${skypulse.swpc.schedule.solar-wind}")
    public CollectorResult collect() {
        return execute();
    }

    @Override
    protected String getSourceName() {
        return "SWPC_SOLAR_WIND";
    }

    @Override
    protected Mono<List<SwpcSolarWindSummary>> fetch() {
        Mono<SwpcSolarWindSummary> speed = swpcApiClient.getRawJson(
                        "/products/summary/solar-wind-speed.json")
                .map(json -> parseFirst(json, SwpcSolarWindSummary.class));
        Mono<SwpcSolarWindSummary> mag = swpcApiClient.getRawJson(
                        "/products/summary/solar-wind-mag-field.json")
                .map(json -> parseFirst(json, SwpcSolarWindSummary.class));

        return Mono.zip(speed, mag, (s, m) -> {
            String ts = s.timeTag() != null ? s.timeTag() : m.timeTag();
            return List.of(new SwpcSolarWindSummary(ts, s.protonSpeed(), m.bt(), m.bzGsm()));
        });
    }

    @Override
    protected boolean validate(SwpcSolarWindSummary item) {
        return item.timeTag() != null
                && (item.protonSpeed() != null || item.bt() != null || item.bzGsm() != null);
    }

    @Override
    protected int persist(List<SwpcSolarWindSummary> data) {
        int count = 0;
        for (var summary : data) {
            OffsetDateTime time = LocalDateTime.parse(summary.timeTag()).atOffset(ZoneOffset.UTC);
            if (solarWindRepo.existsById(time)) continue;

            SolarWindRecord record = new SolarWindRecord();
            record.setTime(time);
            if (summary.protonSpeed() != null) record.setWindSpeed(BigDecimal.valueOf(summary.protonSpeed()));
            if (summary.bt() != null) record.setBt(BigDecimal.valueOf(summary.bt()));
            if (summary.bzGsm() != null) record.setBz(BigDecimal.valueOf(summary.bzGsm()));
            record.setSource("SWPC");
            try {
                record.setRawData(objectMapper.writeValueAsString(summary));
            } catch (JsonProcessingException ignored) {}
            solarWindRepo.save(record);
            count++;
        }
        if (count > 0) solarWindRepo.flush();
        return count;
    }

    private <T> T parseFirst(String json, Class<T> type) {
        try {
            List<T> list = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, type));
            if (list != null && !list.isEmpty()) return list.get(0);
        } catch (JsonProcessingException e) {
            log.error("[SWPC_SOLAR_WIND] Failed to parse response: {}", e.getMessage());
        }
        // Return empty instance
        try {
            return objectMapper.readValue("{}", type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot create empty " + type.getSimpleName(), e);
        }
    }
}
