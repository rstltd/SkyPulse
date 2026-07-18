package com.rstltd.skypulse.collector.wra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.wra.dto.WraReservoirDailyRecord;
import com.rstltd.skypulse.collector.wra.dto.WraReservoirRecord;
import com.rstltd.skypulse.domain.hydrology.Reservoir;
import com.rstltd.skypulse.domain.hydrology.ReservoirStatus;
import com.rstltd.skypulse.repository.ReservoirRepository;
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
    private final ReservoirRepository reservoirDimRepo;
    private final ObjectMapper objectMapper;

    @Value("${skypulse.wra.reservoir-guid}")
    private String reservoirGuid;

    @Value("${skypulse.wra.reservoir-daily-guid}")
    private String reservoirDailyGuid;

    @Value("${skypulse.collectors.eager-startup-load:true}")
    private boolean eagerStartupLoad;

    private volatile Map<String, ReservoirRefData> refDataMap;

    /** storage_pct maps to DECIMAL(6,2); guard against overflow from bad reference capacity. */
    private static final BigDecimal STORAGE_PCT_MAX = new BigDecimal("9999.99");

    public WraReservoirCollector(WraApiClient wraApiClient,
                                 ReservoirStatusRepository reservoirRepo,
                                 ReservoirRepository reservoirDimRepo,
                                 ObjectMapper objectMapper) {
        this.wraApiClient = wraApiClient;
        this.reservoirRepo = reservoirRepo;
        this.reservoirDimRepo = reservoirDimRepo;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        if (!eagerStartupLoad) return;
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
        rs.setWaterLevelM(parseSafe(record.waterlevel()));
        BigDecimal storage = parseSafe(record.effectivewaterstoragecapacity());
        rs.setEffectiveStorageM3(storage);
        rs.setInflowCms(parseSafe(record.inflowdischarge()));
        rs.setOutflowCms(parseSafe(record.totaloutflow()));
        rs.setCatchmentRainMm(parseSafe(record.accumulaterainfallincatchment()));
        rs.setSource("WRA");
        try {
            rs.setRawData(objectMapper.writeValueAsString(record));
        } catch (JsonProcessingException ignored) {}

        // storage_pct = effective storage / design capacity; capacity comes from the dimension
        // reference data. Static name / full level are persisted to the reservoirs dimension.
        ReservoirRefData ref = getRefData().get(record.reservoiridentifier());
        if (ref != null) {
            if (storage != null && ref.capacity() != null
                    && ref.capacity().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal pct = storage.divide(ref.capacity(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
                // A bad reference capacity (unit mismatch) can produce an implausible pct
                // that overflows DECIMAL(6,2) and aborts the whole batch; keep other fields.
                if (pct.abs().compareTo(STORAGE_PCT_MAX) <= 0) {
                    rs.setStoragePct(pct);
                } else {
                    log.warn("[WRA_RESERVOIR] Implausible storage_pct {}% for {} (storage={}, capacity={}); leaving null",
                            pct, record.reservoiridentifier(), storage, ref.capacity());
                }
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
            upsertDimension(map);
            return map;
        } catch (Exception e) {
            log.warn("[WRA_RESERVOIR] Failed to load reference data: {}", e.getMessage());
            return new ConcurrentHashMap<>();
        }
    }

    /**
     * Sync the reservoirs dimension (name / full level / design capacity) from the WRA daily
     * reference data. Coordinates and basin/county are seeded from the GISEPA_P_27 dataset and
     * are preserved here: existing rows keep them, new rows leave them null until seeded.
     */
    private void upsertDimension(Map<String, ReservoirRefData> refData) {
        for (var entry : refData.entrySet()) {
            try {
                ReservoirRefData ref = entry.getValue();
                Reservoir dim = reservoirDimRepo.findById(entry.getKey())
                        .orElseGet(() -> {
                            Reservoir r = new Reservoir();
                            r.setReservoirId(entry.getKey());
                            return r;
                        });
                dim.setReservoirName(ref.name());
                dim.setFullLevelM(ref.fullLevel());
                dim.setDesignCapacityM3(ref.capacity());
                dim.setActive(true);
                reservoirDimRepo.save(dim);
            } catch (Exception e) {
                log.debug("[WRA_RESERVOIR] Dimension upsert failed for {}: {}",
                        entry.getKey(), e.getMessage());
            }
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
