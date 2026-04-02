package com.rstltd.skypulse.collector.swpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.swpc.dto.SwpcDstRow;
import com.rstltd.skypulse.domain.spaceweather.DstIndexRecord;
import com.rstltd.skypulse.repository.DstIndexRecordRepository;
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
                .map(json -> {
                    try {
                        return objectMapper.readValue(json,
                                objectMapper.getTypeFactory().constructCollectionType(
                                        List.class, SwpcDstRow.class));
                    } catch (JsonProcessingException e) {
                        log.error("[SWPC_DST_INDEX] Failed to parse response: {}", e.getMessage());
                        return Collections.<SwpcDstRow>emptyList();
                    }
                });
    }

    @Override
    protected boolean validate(SwpcDstRow item) {
        return item.timeTag() != null && item.dst() != null;
    }

    @Override
    protected int persist(List<SwpcDstRow> data) {
        int count = 0;
        for (var row : data) {
            OffsetDateTime time = LocalDateTime.parse(row.timeTag()).atOffset(ZoneOffset.UTC);
            if (dstRepo.existsById(time)) continue;

            DstIndexRecord record = new DstIndexRecord();
            record.setTime(time);
            record.setDstValue(BigDecimal.valueOf(row.dst()));
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
}
