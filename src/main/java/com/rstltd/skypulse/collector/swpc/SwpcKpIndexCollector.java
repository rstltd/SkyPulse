package com.rstltd.skypulse.collector.swpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.swpc.dto.SwpcKpIndexRow;
import com.rstltd.skypulse.domain.spaceweather.KpIndexRecord;
import com.rstltd.skypulse.repository.KpIndexRecordRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class SwpcKpIndexCollector extends CollectorBase<SwpcKpIndexRow> {

    private final SwpcApiClient swpcApiClient;
    private final KpIndexRecordRepository kpRepo;
    private final ObjectMapper objectMapper;

    public SwpcKpIndexCollector(SwpcApiClient swpcApiClient,
                                KpIndexRecordRepository kpRepo,
                                ObjectMapper objectMapper) {
        this.swpcApiClient = swpcApiClient;
        this.kpRepo = kpRepo;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${skypulse.swpc.schedule.kp-index}")
    public CollectorResult collect() {
        return execute();
    }

    @Override
    protected String getSourceName() {
        return "SWPC_KP_INDEX";
    }

    @Override
    protected Mono<List<SwpcKpIndexRow>> fetch() {
        return swpcApiClient.getRawJson("/products/noaa-planetary-k-index.json")
                .map(this::parseKpArray);
    }

    @Override
    protected boolean validate(SwpcKpIndexRow item) {
        if (item.timeTag() == null || item.kp() == null) return false;
        try {
            double kp = Double.parseDouble(item.kp());
            return kp >= 0 && kp <= 9;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    protected int persist(List<SwpcKpIndexRow> data) {
        int count = 0;
        for (var row : data) {
            OffsetDateTime time = TimeUtils.toUtcOffset(
                    TimeUtils.parseSwpcTimestamp(row.timeTag()));
            if (kpRepo.existsById(time)) continue;

            KpIndexRecord record = new KpIndexRecord();
            record.setTime(time);
            record.setKpValue(new BigDecimal(row.kp()));
            record.setSource("SWPC");
            try {
                record.setRawData(objectMapper.writeValueAsString(row));
            } catch (JsonProcessingException ignored) {}
            kpRepo.save(record);
            count++;
        }
        if (count > 0) kpRepo.flush();
        return count;
    }

    /**
     * Parse SWPC 2D array format:
     * [["time_tag","Kp","a_running","station_count"],
     *  ["2026-03-17 00:00:00.000","2.00","7","8"], ...]
     */
    private List<SwpcKpIndexRow> parseKpArray(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray() || root.size() < 2) return Collections.emptyList();

            List<SwpcKpIndexRow> rows = new ArrayList<>();
            // Skip header row (index 0)
            for (int i = 1; i < root.size(); i++) {
                JsonNode row = root.get(i);
                if (row.isArray() && row.size() >= 2) {
                    rows.add(new SwpcKpIndexRow(row.get(0).asText(), row.get(1).asText()));
                }
            }
            return rows;
        } catch (JsonProcessingException e) {
            log.error("[SWPC_KP_INDEX] Failed to parse Kp array: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
