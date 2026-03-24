package com.rstltd.skypulse.backfill;

import com.rstltd.skypulse.domain.spaceweather.DstIndexRecord;
import com.rstltd.skypulse.domain.spaceweather.KpIndexRecord;
import com.rstltd.skypulse.domain.spaceweather.SolarWindRecord;
import com.rstltd.skypulse.repository.DstIndexRecordRepository;
import com.rstltd.skypulse.repository.KpIndexRecordRepository;
import com.rstltd.skypulse.repository.SolarWindRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Backfill Kp, Dst, and Solar Wind data from NASA OMNIWeb (2019-present).
 * Processes month by month, writes to all 3 repositories simultaneously.
 */
@Component
public class OmniWebHistoricalBackfill {

    private static final Logger log = LoggerFactory.getLogger(OmniWebHistoricalBackfill.class);

    private final OmniWebClient omniWebClient;
    private final KpIndexRecordRepository kpRepo;
    private final DstIndexRecordRepository dstRepo;
    private final SolarWindRecordRepository solarWindRepo;

    public OmniWebHistoricalBackfill(OmniWebClient omniWebClient,
                                      KpIndexRecordRepository kpRepo,
                                      DstIndexRecordRepository dstRepo,
                                      SolarWindRecordRepository solarWindRepo) {
        this.omniWebClient = omniWebClient;
        this.kpRepo = kpRepo;
        this.dstRepo = dstRepo;
        this.solarWindRepo = solarWindRepo;
    }

    public BackfillResult execute(LocalDate startDate, LocalDate endDate) {
        long start = System.currentTimeMillis();
        int totalFetched = 0;
        int kpInserted = 0, dstInserted = 0, swInserted = 0;
        int skipped = 0;

        try {
            LocalDate current = startDate.withDayOfMonth(1);
            while (!current.isAfter(endDate)) {
                LocalDate monthEnd = current.plusMonths(1).minusDays(1);
                if (monthEnd.isAfter(endDate)) monthEnd = endDate;

                log.info("[OMNIWEB_BACKFILL] Processing {} to {}", current, monthEnd);

                List<OmniWebClient.OmniWebRecord> records = omniWebClient.query(current, monthEnd);
                totalFetched += records.size();

                for (var rec : records) {
                    OffsetDateTime time = toOffsetDateTime(rec);

                    // Kp
                    if (rec.kpTenths() != null) {
                        if (!kpRepo.existsById(time)) {
                            KpIndexRecord kp = new KpIndexRecord();
                            kp.setTime(time);
                            kp.setKpValue(BigDecimal.valueOf(rec.kpTenths() / 10.0));
                            kp.setSource("OMNIWEB");
                            kpRepo.save(kp);
                            kpInserted++;
                        } else {
                            skipped++;
                        }
                    }

                    // Dst
                    if (rec.dst() != null) {
                        if (!dstRepo.existsById(time)) {
                            DstIndexRecord dst = new DstIndexRecord();
                            dst.setTime(time);
                            dst.setDstValue(BigDecimal.valueOf(rec.dst()));
                            dst.setSource("OMNIWEB");
                            dstRepo.save(dst);
                            dstInserted++;
                        } else {
                            skipped++;
                        }
                    }

                    // Solar Wind
                    if (rec.windSpeed() != null || rec.bz() != null) {
                        if (!solarWindRepo.existsById(time)) {
                            SolarWindRecord sw = new SolarWindRecord();
                            sw.setTime(time);
                            if (rec.windSpeed() != null) sw.setWindSpeed(BigDecimal.valueOf(rec.windSpeed()));
                            if (rec.density() != null) sw.setDensity(BigDecimal.valueOf(rec.density()));
                            if (rec.bz() != null) sw.setBz(BigDecimal.valueOf(rec.bz()));
                            sw.setSource("OMNIWEB");
                            solarWindRepo.save(sw);
                            swInserted++;
                        } else {
                            skipped++;
                        }
                    }
                }

                kpRepo.flush();
                dstRepo.flush();
                solarWindRepo.flush();

                log.info("[OMNIWEB_BACKFILL] Month done: {} records, kp={}, dst={}, sw={}",
                        records.size(), kpInserted, dstInserted, swInserted);

                current = current.plusMonths(1);
            }

            long duration = System.currentTimeMillis() - start;
            int totalInserted = kpInserted + dstInserted + swInserted;
            return BackfillResult.success("omniweb-space-weather",
                    startDate.toString(), endDate.toString(),
                    totalFetched, totalInserted, skipped, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[OMNIWEB_BACKFILL] Failed: {}", e.getMessage());
            return BackfillResult.error("omniweb-space-weather",
                    startDate.toString(), endDate.toString(), duration, e.getMessage());
        }
    }

    private OffsetDateTime toOffsetDateTime(OmniWebClient.OmniWebRecord rec) {
        LocalDateTime ldt = LocalDate.ofYearDay(rec.year(), rec.doy())
                .atTime(rec.hour(), 0);
        return ldt.atOffset(ZoneOffset.UTC);
    }
}
