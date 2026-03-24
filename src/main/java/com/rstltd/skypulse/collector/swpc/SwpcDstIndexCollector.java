package com.rstltd.skypulse.collector.swpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.swpc.dto.SwpcDstRow;
import com.rstltd.skypulse.domain.spaceweather.DstIndexRecord;
import com.rstltd.skypulse.repository.DstIndexRecordRepository;
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
public class SwpcDstIndexCollector extends CollectorBase<SwpcDstRow> {

    private final SwpcApiClient swpcApiClient;
    private final DstIndexRecordRepository dstRepo;
    private final ObjectMapper objectMapper;

    public SwpcDstIndexCollector(SwpcApiClient swpcApiClient,
                                 DstIndexRecordRepository dstRepo,
                                 ObjectMapper objectMapper) {
        this.swpcApiClient = swpcApiClient;
        this.dstRepo = dstRepo;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${skypulse.swpc.schedule.dst-index}")
    public CollectorResult collect() {
        return execute();
    }

    @Override
    protected String getSourceName() {
        return "SWPC_DST_INDEX";
    }

    @Override
    protected Mono<List<SwpcDstRow>> fetch() {
        return swpcApiClient.getRawJson("/products/kyoto-dst.json")
                .map(this::parseDstArray);
    }

    @Override
    protected boolean validate(SwpcDstRow item) {
        if (item.timeTag() == null || item.dst() == null) return false;
        try {
            Double.parseDouble(item.dst());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    protected int persist(List<SwpcDstRow> data) {
        int count = 0;
        for (var row : data) {
            OffsetDateTime time = TimeUtils.toUtcOffset(
                    TimeUtils.parseSwpcTimestamp(row.timeTag()));
            if (dstRepo.existsById(time)) continue;

            DstIndexRecord record = new DstIndexRecord();
            record.setTime(time);
            record.setDstValue(new BigDecimal(row.dst()));
            record.setSource("SWPC");
            try {
                record.setRawData(objectMapper.writeValueAsString(row));
            } catch (JsonProcessingException ignored) {}
            dstRepo.save(record);
            count++;
        }
        if (count > 0) dstRepo.flush();
        return count;
    }

    private List<SwpcDstRow> parseDstArray(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray() || root.size() < 2) return Collections.emptyList();

            List<SwpcDstRow> rows = new ArrayList<>();
            for (int i = 1; i < root.size(); i++) {
                JsonNode row = root.get(i);
                if (row.isArray() && row.size() >= 2) {
                    rows.add(new SwpcDstRow(row.get(0).asText(), row.get(1).asText()));
                }
            }
            return rows;
        } catch (JsonProcessingException e) {
            log.error("[SWPC_DST_INDEX] Failed to parse Dst array: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
