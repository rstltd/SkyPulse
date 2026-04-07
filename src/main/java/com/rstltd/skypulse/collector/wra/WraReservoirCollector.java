package com.rstltd.skypulse.collector.wra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.wra.dto.WraReservoirDailyRecord;
import com.rstltd.skypulse.collector.wra.dto.WraReservoirRecord;
import com.rstltd.skypulse.domain.hydrology.ReservoirStatus;
import com.rstltd.skypulse.repository.ReservoirStatusRepository;
import com.rstltd.skypulse.util.TimeUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class WraReservoirCollector extends CollectorBase<WraReservoirRecord> {

    private final WraApiClient wraApiClient;
    private final ReservoirStatusRepository reservoirRepo;
    private final ObjectMapper objectMapper;

    @Value("${skypulse.wra.reservoir-guid}")
    private String reservoirGuid;

    @Value("${skypulse.wra.reservoir-daily-guid}")
    private String reservoirDailyGuid;

    private volatile Map<String, ReservoirRefData> refDataMap;

    public WraReservoirCollector(WraApiClient wraApiClient,
                                 ReservoirStatusRepository reservoirRepo,
                                 ObjectMapper objectMapper) {
        this.wraApiClient = wraApiClient;
        this.reservoirRepo = reservoirRepo;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        try {
            refDataMap = loadRefData();
        } catch (Exception e) {
            log.warn("[WRA_RESERVOIR] Failed to load reference data at startup: {}", e.getMessage());
            refDataMap = new ConcurrentHashMap<>();
        }
        // Run initial collection so data is available immediately after startup
        try {
            execute();
        } catch (Exception e) {
            log.warn("[WRA_RESERVOIR] Initial collection failed: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${skypulse.wra.schedule.reservoir}")
    public CollectorResult collect() {
        return execute();
    }

    @Scheduled(cron = "0 0 6 * * *")
    public void refreshRefData() {
        refDataMap = loadRefData();
        log.info("[WRA_RESERVOIR] Refreshed reference data: {} reservoirs", refDataMap.size());
    }

    @Override
    protected String getSourceName() {
        return "WRA_RESERVOIR";
    }

    @Override
    protected Mono<List<WraReservoirRecord>> fetch() {
        return wraApiClient.getDataset(reservoirGuid)
                .map(json -> {
                    try {
                        return objectMapper.readValue(json,
                                objectMapper.getTypeFactory().constructCollectionType(
                                        List.class, WraReservoirRecord.class));
                    } catch (JsonProcessingException e) {
                        log.error("[WRA_RESERVOIR] Failed to parse response: {}", e.getMessage());
                        return Collections.<WraReservoirRecord>emptyList();
                    }
                });
    }

    @Override
    protected boolean validate(WraReservoirRecord item) {
        if (item.reservoiridentifier() == null || item.reservoiridentifier().isBlank()
                || item.observationtime() == null || item.observationtime().isBlank()) {
            return false;
        }
        // Skip reservoirs without reference data (no name/capacity available)
        return getRefData().containsKey(item.reservoiridentifier());
    }

    @Override
    protected int persist(List<WraReservoirRecord> data) {
        if (data.isEmpty()) return 0;

        OffsetDateTime batchTime = TimeUtils.toUtcOffset(
                TimeUtils.parseWraTimestamp(data.get(0).observationtime()));
        Set<String> existingKeys = reservoirRepo
                .findByTimeBetween(batchTime.minusHours(1), batchTime.plusHours(1))
                .stream()
                .map(r -> r.getTime() + "|" + r.getReservoirId())
                .collect(Collectors.toSet());

        List<ReservoirStatus> newObs = data.stream()
                .map(this::mapToEntity)
                .filter(obs -> !existingKeys.contains(obs.getTime() + "|" + obs.getReservoirId()))
                .toList();

        if (!newObs.isEmpty()) {
            reservoirRepo.saveAll(newObs);
            reservoirRepo.flush();
        }
        return newObs.size();
    }

    private ReservoirStatus mapToEntity(WraReservoirRecord record) {
        ReservoirStatus rs = new ReservoirStatus();
        rs.setTime(TimeUtils.toUtcOffset(TimeUtils.parseWraTimestamp(record.observationtime())));
        rs.setReservoirId(record.reservoiridentifier());
        rs.setWaterLevel(parseSafe(record.waterlevel()));
        rs.setInflow(parseSafe(record.inflowdischarge()));
        rs.setOutflow(parseSafe(record.totaloutflow()));
        rs.setDailyRainfall(parseSafe(record.accumulaterainfallincatchment()));
        rs.setSource("WRA");
        try {
            rs.setRawData(objectMapper.writeValueAsString(record));
        } catch (JsonProcessingException ignored) {}

        ReservoirRefData ref = getRefData().get(record.reservoiridentifier());
        if (ref != null) {
            rs.setReservoirName(ref.name());
            rs.setFullLevel(ref.fullLevel());
            BigDecimal storage = parseSafe(record.effectivewaterstoragecapacity());
            if (storage != null && ref.capacity() != null
                    && ref.capacity().compareTo(BigDecimal.ZERO) > 0) {
                rs.setStoragePct(storage.divide(ref.capacity(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP));
            }
        } else {
            log.debug("[WRA_RESERVOIR] No reference data for reservoir: {}",
                    record.reservoiridentifier());
        }

        return rs;
    }

    private Map<String, ReservoirRefData> getRefData() {
        if (refDataMap == null) {
            refDataMap = loadRefData();
        }
        return refDataMap;
    }

    private Map<String, ReservoirRefData> loadRefData() {
        try {
            String json = wraApiClient.getDataset(reservoirDailyGuid).block();
            List<WraReservoirDailyRecord> records = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, WraReservoirDailyRecord.class));

            Map<String, ReservoirRefData> map = new ConcurrentHashMap<>();
            for (WraReservoirDailyRecord r : records) {
                if (r.reservoiridentifier() != null && !r.reservoiridentifier().isBlank()) {
                    map.putIfAbsent(r.reservoiridentifier(), new ReservoirRefData(
                            r.reservoirname(),
                            parseSafe(r.nwlmax()),
                            parseSafe(r.capacity())
                    ));
                }
            }
            log.info("[WRA_RESERVOIR] Loaded reference data for {} reservoirs", map.size());
            return map;
        } catch (Exception e) {
            log.warn("[WRA_RESERVOIR] Failed to load reference data: {}", e.getMessage());
            return new ConcurrentHashMap<>();
        }
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
