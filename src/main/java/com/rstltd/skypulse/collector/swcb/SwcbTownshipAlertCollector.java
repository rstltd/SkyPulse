package com.rstltd.skypulse.collector.swcb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.swcb.dto.SwcbTownAlertRecord;
import com.rstltd.skypulse.domain.alert.TownshipAlertBaseline;
import com.rstltd.skypulse.domain.alert.TownshipAlertBaselineId;
import com.rstltd.skypulse.repository.TownshipAlertBaselineRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the SWCB township-level debris-flow alert baselines (GetCountyTownAlertValueList) into
 * {@code township_alert_baseline}. A township may report several sub-area baselines; they are
 * reduced to the safest (minimum) R70 threshold per (county, town) so the coarse township lookup
 * errs toward earlier warning.
 */
@Component
public class SwcbTownshipAlertCollector extends CollectorBase<SwcbTownAlertRecord> {

    private final SwcbApiClient swcbApiClient;
    private final TownshipAlertBaselineRepository townshipRepo;
    private final ObjectMapper objectMapper;

    @Value("${skypulse.collectors.eager-startup-load:true}")
    private boolean eagerStartupLoad;

    public SwcbTownshipAlertCollector(SwcbApiClient swcbApiClient,
                                      TownshipAlertBaselineRepository townshipRepo,
                                      ObjectMapper objectMapper) {
        this.swcbApiClient = swcbApiClient;
        this.townshipRepo = townshipRepo;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        if (!eagerStartupLoad) return;
        try {
            execute();
        } catch (Exception e) {
            log.warn("[SWCB_TOWNSHIP_ALERT] Initial load failed: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${skypulse.swcb.schedule.township-alert}")
    public CollectorResult collect() {
        return execute();
    }

    @Override
    protected String getSourceName() {
        return "SWCB_TOWNSHIP_ALERT";
    }

    @Override
    protected Mono<List<SwcbTownAlertRecord>> fetch() {
        return swcbApiClient.getRawJson("/WebService/GetCountyTownAlertValueList.ashx")
                .map(json -> {
                    try {
                        return objectMapper.readValue(json,
                                objectMapper.getTypeFactory().constructCollectionType(
                                        List.class, SwcbTownAlertRecord.class));
                    } catch (JsonProcessingException e) {
                        log.error("[SWCB_TOWNSHIP_ALERT] Failed to parse response: {}", e.getMessage());
                        return Collections.<SwcbTownAlertRecord>emptyList();
                    }
                });
    }

    @Override
    protected boolean validate(SwcbTownAlertRecord item) {
        return item.County() != null && !item.County().isBlank()
                && item.Town() != null && !item.Town().isBlank()
                && item.AlertValue() != null;
    }

    @Override
    protected int persist(List<SwcbTownAlertRecord> data) {
        // Reduce duplicate (county, town) rows to the minimum (safest) threshold.
        Map<TownshipAlertBaselineId, BigDecimal> minByTownship = new LinkedHashMap<>();
        for (var r : data) {
            var id = new TownshipAlertBaselineId(r.County(), r.Town());
            minByTownship.merge(id, r.AlertValue(), BigDecimal::min);
        }

        int count = 0;
        for (var entry : minByTownship.entrySet()) {
            TownshipAlertBaselineId id = entry.getKey();
            TownshipAlertBaseline t = townshipRepo.findById(id).orElseGet(() -> {
                TownshipAlertBaseline n = new TownshipAlertBaseline();
                n.setCounty(id.getCounty());
                n.setTown(id.getTown());
                return n;
            });
            t.setAlertValue(entry.getValue());
            townshipRepo.save(t);
            count++;
        }
        if (count > 0) townshipRepo.flush();
        return count;
    }
}
