package com.rstltd.skypulse.collector.swpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.swpc.dto.SwpcSolarWindSummary;
import com.rstltd.skypulse.domain.spaceweather.SolarWindRecord;
import com.rstltd.skypulse.repository.SolarWindRecordRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
        Mono<SwpcSolarWindSummary> speed = swpcApiClient.get(
                "/products/summary/solar-wind-speed.json", SwpcSolarWindSummary.class);
        Mono<SwpcSolarWindSummary> mag = swpcApiClient.get(
                "/products/summary/solar-wind-mag-field.json", SwpcSolarWindSummary.class);

        return Mono.zip(speed, mag, (s, m) -> {
            // Merge speed and mag field into one summary using speed's timestamp
            String ts = s.timeStamp() != null ? s.timeStamp() : m.timeStamp();
            return List.of(new SwpcSolarWindSummary(ts, s.windSpeed(), m.bt(), m.bz()));
        });
    }

    @Override
    protected boolean validate(SwpcSolarWindSummary item) {
        return item.timeStamp() != null
                && (item.windSpeed() != null || item.bt() != null || item.bz() != null);
    }

    @Override
    protected int persist(List<SwpcSolarWindSummary> data) {
        int count = 0;
        for (var summary : data) {
            OffsetDateTime time = TimeUtils.toUtcOffset(
                    TimeUtils.parseSwpcTimestamp(summary.timeStamp()));
            if (solarWindRepo.existsById(time)) continue;

            SolarWindRecord record = new SolarWindRecord();
            record.setTime(time);
            record.setWindSpeed(parseSafe(summary.windSpeed()));
            record.setBt(parseSafe(summary.bt()));
            record.setBz(parseSafe(summary.bz()));
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

    private BigDecimal parseSafe(String value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
