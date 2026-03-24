package com.rstltd.skypulse.collector.swpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.swpc.dto.SwpcAlertEntry;
import com.rstltd.skypulse.domain.spaceweather.SpaceWeatherAlert;
import com.rstltd.skypulse.repository.SpaceWeatherAlertRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SwpcAlertCollector extends CollectorBase<SwpcAlertEntry> {

    private static final Pattern SERIAL_NUMBER_PATTERN =
            Pattern.compile("Serial Number:\\s*(\\d+)");

    private final SwpcApiClient swpcApiClient;
    private final SpaceWeatherAlertRepository alertRepo;
    private final ObjectMapper objectMapper;

    public SwpcAlertCollector(SwpcApiClient swpcApiClient,
                              SpaceWeatherAlertRepository alertRepo,
                              ObjectMapper objectMapper) {
        this.swpcApiClient = swpcApiClient;
        this.alertRepo = alertRepo;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${skypulse.swpc.schedule.alert}")
    public CollectorResult collect() {
        return execute();
    }

    @Override
    protected String getSourceName() {
        return "SWPC_ALERT";
    }

    @Override
    protected Mono<List<SwpcAlertEntry>> fetch() {
        return swpcApiClient.getRawJson("/products/alerts.json")
                .map(json -> {
                    try {
                        return objectMapper.readValue(json,
                                objectMapper.getTypeFactory().constructCollectionType(
                                        List.class, SwpcAlertEntry.class));
                    } catch (JsonProcessingException e) {
                        log.error("[SWPC_ALERT] Failed to parse alerts: {}", e.getMessage());
                        return Collections.<SwpcAlertEntry>emptyList();
                    }
                });
    }

    @Override
    protected boolean validate(SwpcAlertEntry item) {
        return item.productId() != null
                && item.issueDatetime() != null
                && item.message() != null;
    }

    @Override
    protected int persist(List<SwpcAlertEntry> data) {
        int count = 0;
        for (var entry : data) {
            String serialNumber = extractSerialNumber(entry.message());
            if (serialNumber != null && alertRepo.findBySerialNumber(serialNumber).isPresent()) {
                continue;
            }

            SpaceWeatherAlert alert = new SpaceWeatherAlert();
            alert.setAlertTime(TimeUtils.toUtcOffset(
                    TimeUtils.parseSwpcTimestamp(entry.issueDatetime())));
            alert.setAlertType(entry.productId());
            alert.setSerialNumber(serialNumber);
            alert.setMessage(entry.message());
            alert.setSource("SWPC");
            try {
                alert.setRawData(objectMapper.writeValueAsString(entry));
            } catch (JsonProcessingException ignored) {}
            alertRepo.save(alert);
            count++;
        }
        if (count > 0) alertRepo.flush();
        return count;
    }

    private String extractSerialNumber(String message) {
        if (message == null) return null;
        Matcher matcher = SERIAL_NUMBER_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }
}
