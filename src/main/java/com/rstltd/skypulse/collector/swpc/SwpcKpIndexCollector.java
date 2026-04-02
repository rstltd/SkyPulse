package com.rstltd.skypulse.collector.swpc;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
                .map(json -> {
                    try {
                        return objectMapper.readValue(json,
                                objectMapper.getTypeFactory().constructCollectionType(
                                        List.class, SwpcKpIndexRow.class));
                    } catch (JsonProcessingException e) {
                        log.error("[SWPC_KP_INDEX] Failed to parse response: {}", e.getMessage());
                        return Collections.<SwpcKpIndexRow>emptyList();
                    }
                });
    }

    @Override
    protected boolean validate(SwpcKpIndexRow item) {
        if (item.timeTag() == null || item.kp() == null) return false;
        return item.kp() >= 0 && item.kp() <= 9;
    }

    @Override
    protected int persist(List<SwpcKpIndexRow> data) {
        int count = 0;
        for (var row : data) {
            OffsetDateTime time = parseSwpcIsoTime(row.timeTag());
            if (kpRepo.existsById(time)) continue;

            KpIndexRecord record = new KpIndexRecord();
            record.setTime(time);
            record.setKpValue(BigDecimal.valueOf(row.kp()));
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

    private OffsetDateTime parseSwpcIsoTime(String timeTag) {
        return LocalDateTime.parse(timeTag).atOffset(ZoneOffset.UTC);
    }
}
