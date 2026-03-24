package com.rstltd.skypulse.backfill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.swpc.SwpcApiClient;
import com.rstltd.skypulse.domain.spaceweather.KpIndexRecord;
import com.rstltd.skypulse.repository.KpIndexRecordRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Component
public class SwpcKpHistoricalBackfill {

    private static final Logger log = LoggerFactory.getLogger(SwpcKpHistoricalBackfill.class);

    private final SwpcApiClient swpcApiClient;
    private final KpIndexRecordRepository kpRepo;
    private final ObjectMapper objectMapper;

    public SwpcKpHistoricalBackfill(SwpcApiClient swpcApiClient,
                                     KpIndexRecordRepository kpRepo,
                                     ObjectMapper objectMapper) {
        this.swpcApiClient = swpcApiClient;
        this.kpRepo = kpRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Backfill Kp index using SWPC real-time API (only ~7 days of history available).
     */
    public BackfillResult execute(LocalDate startDate, LocalDate endDate) {
        long start = System.currentTimeMillis();
        int totalFetched = 0;
        int inserted = 0;
        int skipped = 0;

        try {
            String json = swpcApiClient.getRawJson("/products/noaa-planetary-k-index.json")
                    .block(Duration.ofSeconds(30));
            if (json == null) {
                return BackfillResult.error("swpc-kp", startDate.toString(), endDate.toString(),
                        System.currentTimeMillis() - start, "No data returned from SWPC API");
            }

            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray() || root.size() < 2) {
                return BackfillResult.error("swpc-kp", startDate.toString(), endDate.toString(),
                        System.currentTimeMillis() - start, "Invalid response format");
            }

            for (int i = 1; i < root.size(); i++) {
                JsonNode row = root.get(i);
                if (!row.isArray() || row.size() < 2) continue;

                String timeTag = row.get(0).asText();
                String kpValue = row.get(1).asText();
                totalFetched++;

                try {
                    OffsetDateTime time = TimeUtils.toUtcOffset(TimeUtils.parseSwpcTimestamp(timeTag));
                    LocalDate recordDate = time.toLocalDate();

                    if (recordDate.isBefore(startDate) || recordDate.isAfter(endDate)) {
                        continue;
                    }

                    double kp = Double.parseDouble(kpValue);
                    if (kp < 0 || kp > 9) continue;

                    if (kpRepo.existsById(time)) {
                        skipped++;
                        continue;
                    }

                    KpIndexRecord record = new KpIndexRecord();
                    record.setTime(time);
                    record.setKpValue(new BigDecimal(kpValue));
                    record.setSource("SWPC");
                    kpRepo.save(record);
                    inserted++;
                } catch (Exception e) {
                    log.debug("[SWPC_KP_BACKFILL] Skipping row {}: {}", timeTag, e.getMessage());
                }
            }
            if (inserted > 0) kpRepo.flush();

            long duration = System.currentTimeMillis() - start;
            return BackfillResult.success("swpc-kp", startDate.toString(), endDate.toString(),
                    totalFetched, inserted, skipped, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[SWPC_KP_BACKFILL] Failed: {}", e.getMessage());
            return BackfillResult.error("swpc-kp", startDate.toString(), endDate.toString(),
                    duration, e.getMessage());
        }
    }
}
