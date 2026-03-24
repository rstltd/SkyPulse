package com.rstltd.skypulse.backfill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class BackfillService {

    private static final Logger log = LoggerFactory.getLogger(BackfillService.class);

    @Value("${skypulse.backfill.enabled}")
    private boolean enabled;

    private final UsgsHistoricalBackfill usgsBackfill;
    private final SwpcKpHistoricalBackfill swpcKpBackfill;
    private final SwpcDstHistoricalBackfill swpcDstBackfill;
    private final OmniWebHistoricalBackfill omniWebBackfill;

    public BackfillService(UsgsHistoricalBackfill usgsBackfill,
                           SwpcKpHistoricalBackfill swpcKpBackfill,
                           SwpcDstHistoricalBackfill swpcDstBackfill,
                           OmniWebHistoricalBackfill omniWebBackfill) {
        this.usgsBackfill = usgsBackfill;
        this.swpcKpBackfill = swpcKpBackfill;
        this.swpcDstBackfill = swpcDstBackfill;
        this.omniWebBackfill = omniWebBackfill;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public BackfillResult executeBackfill(String source, LocalDate startDate, LocalDate endDate) {
        if (!enabled) {
            return BackfillResult.error(source, startDate.toString(), endDate.toString(),
                    0, "Backfill is disabled. Set skypulse.backfill.enabled=true to enable.");
        }

        log.info("[BACKFILL] Starting {} from {} to {}", source, startDate, endDate);

        BackfillResult result = switch (source) {
            case "usgs-earthquake" -> usgsBackfill.execute(startDate, endDate);
            case "swpc-kp" -> swpcKpBackfill.execute(startDate, endDate);
            case "swpc-dst" -> swpcDstBackfill.execute(startDate, endDate);
            case "omniweb-space-weather" -> omniWebBackfill.execute(startDate, endDate);
            default -> BackfillResult.error(source, startDate.toString(), endDate.toString(),
                    0, "Unknown backfill source: " + source);
        };

        log.info("[BACKFILL] {} completed: {}", source, result.message());
        return result;
    }
}
