package com.rstltd.skypulse.collector.swpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.swpc.dto.SwpcNoaaScalesEntry;
import com.rstltd.skypulse.domain.spaceweather.NoaaScale;
import com.rstltd.skypulse.domain.spaceweather.NoaaScaleId;
import com.rstltd.skypulse.repository.NoaaScaleRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Collects SWPC products/noaa-scales.json — the continuously-published NOAA G/R/S scales
 * (current observed + 3-day forecast). Populates the {@code noaa_scales} table that feeds the
 * GNSS-quality G branch; the alert collector never parsed these scales.
 */
@Component
public class SwpcNoaaScalesCollector extends CollectorBase<SwpcNoaaScalesCollector.ScaleReading> {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Map the JSON day-offset key to a horizon label; entries not listed here are ignored. */
    private static final Map<String, String> HORIZONS = Map.of(
            "0", "observed",
            "1", "predicted_d1",
            "2", "predicted_d2",
            "3", "predicted_d3");

    /** Flattened one-horizon reading, stamped with the fetch's common issue time. */
    public record ScaleReading(String horizon, OffsetDateTime time,
                               Integer g, Integer r, Integer s, String rawJson) {}

    private final SwpcApiClient swpcApiClient;
    private final NoaaScaleRepository noaaScaleRepo;
    private final ObjectMapper objectMapper;

    public SwpcNoaaScalesCollector(SwpcApiClient swpcApiClient,
                                   NoaaScaleRepository noaaScaleRepo,
                                   ObjectMapper objectMapper) {
        this.swpcApiClient = swpcApiClient;
        this.noaaScaleRepo = noaaScaleRepo;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${skypulse.swpc.schedule.noaa-scales}")
    public CollectorResult collect() {
        return execute();
    }

    @Override
    protected String getSourceName() {
        return "SWPC_NOAA_SCALES";
    }

    @Override
    protected Mono<List<ScaleReading>> fetch() {
        return swpcApiClient.getRawJson("/products/noaa-scales.json")
                .map(json -> {
                    try {
                        Map<String, SwpcNoaaScalesEntry> doc = objectMapper.readValue(json,
                                objectMapper.getTypeFactory().constructMapType(
                                        Map.class, String.class, SwpcNoaaScalesEntry.class));
                        return toReadings(doc);
                    } catch (JsonProcessingException e) {
                        log.error("[SWPC_NOAA_SCALES] Failed to parse response: {}", e.getMessage());
                        return Collections.<ScaleReading>emptyList();
                    }
                });
    }

    /** Use the observed ("0") entry's stamp as the common issue time for the whole fetch. */
    private List<ScaleReading> toReadings(Map<String, SwpcNoaaScalesEntry> doc) {
        SwpcNoaaScalesEntry observed = doc.get("0");
        if (observed == null) return Collections.emptyList();
        OffsetDateTime issueTime = parseStamp(observed);
        if (issueTime == null) return Collections.emptyList();

        List<ScaleReading> readings = new ArrayList<>();
        for (var entry : HORIZONS.entrySet()) {
            SwpcNoaaScalesEntry e = doc.get(entry.getKey());
            if (e == null) continue;
            readings.add(new ScaleReading(entry.getValue(), issueTime,
                    scale(e.G()), scale(e.R()), scale(e.S()), rawJson(e)));
        }
        return readings;
    }

    @Override
    protected boolean validate(ScaleReading item) {
        // Keep any horizon that carries at least one scale value; time is guaranteed non-null.
        return item.time() != null
                && (item.g() != null || item.r() != null || item.s() != null);
    }

    @Override
    protected int persist(List<ScaleReading> data) {
        int count = 0;
        for (var r : data) {
            if (noaaScaleRepo.existsById(new NoaaScaleId(r.time(), r.horizon()))) continue;
            NoaaScale ns = new NoaaScale();
            ns.setTime(r.time());
            ns.setHorizon(r.horizon());
            ns.setGScale(r.g());
            ns.setRScale(r.r());
            ns.setSScale(r.s());
            ns.setSource("SWPC");
            ns.setRawData(r.rawJson());
            noaaScaleRepo.save(ns);
            count++;
        }
        if (count > 0) noaaScaleRepo.flush();
        return count;
    }

    private OffsetDateTime parseStamp(SwpcNoaaScalesEntry e) {
        if (e.DateStamp() == null || e.TimeStamp() == null) return null;
        try {
            return LocalDateTime.parse(e.DateStamp() + " " + e.TimeStamp(), STAMP)
                    .atOffset(ZoneOffset.UTC);
        } catch (Exception ex) {
            log.warn("[SWPC_NOAA_SCALES] Bad timestamp {} {}: {}",
                    e.DateStamp(), e.TimeStamp(), ex.getMessage());
            return null;
        }
    }

    private Integer scale(SwpcNoaaScalesEntry.Scale s) {
        if (s == null || s.Scale() == null) return null;
        try {
            return Integer.parseInt(s.Scale().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String rawJson(SwpcNoaaScalesEntry e) {
        try {
            return objectMapper.writeValueAsString(e);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
