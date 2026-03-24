package com.rstltd.skypulse.backfill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.swpc.SwpcApiClient;
import com.rstltd.skypulse.domain.spaceweather.DstIndexRecord;
import com.rstltd.skypulse.repository.DstIndexRecordRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Component
public class SwpcDstHistoricalBackfill {

    private static final Logger log = LoggerFactory.getLogger(SwpcDstHistoricalBackfill.class);

    private final SwpcApiClient swpcApiClient;
    private final DstIndexRecordRepository dstRepo;
    private final ObjectMapper objectMapper;

    public SwpcDstHistoricalBackfill(SwpcApiClient swpcApiClient,
                                      DstIndexRecordRepository dstRepo,
                                      ObjectMapper objectMapper) {
        this.swpcApiClient = swpcApiClient;
        this.dstRepo = dstRepo;
        this.objectMapper = objectMapper;
    }

    public BackfillResult execute(LocalDate startDate, LocalDate endDate) {
        long start = System.currentTimeMillis();
        int totalFetched = 0;
        int inserted = 0;
        int skipped = 0;

        try {
            String json = swpcApiClient.getRawJson("/products/kyoto-dst.json")
                    .block(Duration.ofSeconds(30));
            if (json == null) {
                return BackfillResult.error("swpc-dst", startDate.toString(), endDate.toString(),
                        System.currentTimeMillis() - start, "No data returned from SWPC API");
            }

            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray() || root.size() < 2) {
                return BackfillResult.error("swpc-dst", startDate.toString(), endDate.toString(),
                        System.currentTimeMillis() - start, "Invalid response format");
            }

            for (int i = 1; i < root.size(); i++) {
                JsonNode row = root.get(i);
                if (!row.isArray() || row.size() < 2) continue;

                String timeTag = row.get(0).asText();
                String dstValue = row.get(1).asText();
                totalFetched++;

                try {
                    OffsetDateTime time = TimeUtils.toUtcOffset(TimeUtils.parseSwpcTimestamp(timeTag));
                    LocalDate recordDate = time.toLocalDate();

                    if (recordDate.isBefore(startDate) || recordDate.isAfter(endDate)) continue;

                    if (dstRepo.existsById(time)) {
                        skipped++;
                        continue;
                    }

                    DstIndexRecord record = new DstIndexRecord();
                    record.setTime(time);
                    record.setDstValue(new BigDecimal(dstValue));
                    record.setSource("SWPC");
                    dstRepo.save(record);
                    inserted++;
                } catch (Exception e) {
                    log.debug("[SWPC_DST_BACKFILL] Skipping row {}: {}", timeTag, e.getMessage());
                }
            }
            if (inserted > 0) dstRepo.flush();

            long duration = System.currentTimeMillis() - start;
            return BackfillResult.success("swpc-dst", startDate.toString(), endDate.toString(),
                    totalFetched, inserted, skipped, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[SWPC_DST_BACKFILL] Failed: {}", e.getMessage());
            return BackfillResult.error("swpc-dst", startDate.toString(), endDate.toString(),
                    duration, e.getMessage());
        }
    }
}
