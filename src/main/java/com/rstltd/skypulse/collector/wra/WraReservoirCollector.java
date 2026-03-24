package com.rstltd.skypulse.collector.wra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.wra.dto.WraReservoirRecord;
import com.rstltd.skypulse.domain.hydrology.ReservoirStatus;
import com.rstltd.skypulse.repository.ReservoirStatusRepository;
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
public class WraReservoirCollector extends CollectorBase<WraReservoirRecord> {

    private final WraApiClient wraApiClient;
    private final ReservoirStatusRepository reservoirRepo;
    private final ObjectMapper objectMapper;

    @Value("${skypulse.wra.reservoir-guid}")
    private String reservoirGuid;

    public WraReservoirCollector(WraApiClient wraApiClient,
                                 ReservoirStatusRepository reservoirRepo,
                                 ObjectMapper objectMapper) {
        this.wraApiClient = wraApiClient;
        this.reservoirRepo = reservoirRepo;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${skypulse.wra.schedule.reservoir}")
    public CollectorResult collect() {
        return execute();
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
        return item.reservoiridentifier() != null && !item.reservoiridentifier().isBlank()
                && item.observationtime() != null && !item.observationtime().isBlank();
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
        return rs;
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
