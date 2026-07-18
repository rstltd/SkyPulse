package com.rstltd.skypulse.collector.swcb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.swcb.dto.SwcbDebrisRecord;
import com.rstltd.skypulse.domain.alert.DebrisStream;
import com.rstltd.skypulse.repository.DebrisStreamRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads the SWCB potential debris-flow streams (GetDebrisRainData) into {@code debris_stream}:
 * per-stream R70 alert baseline (mm) plus up to two reference rainfall stations with blend ratios.
 * Keyed by the unique {@code DebrisNO}; upserted so daily refreshes update in place.
 */
@Component
public class SwcbDebrisCollector extends CollectorBase<SwcbDebrisRecord> {

    private final SwcbApiClient swcbApiClient;
    private final DebrisStreamRepository debrisRepo;
    private final ObjectMapper objectMapper;

    @Value("${skypulse.collectors.eager-startup-load:true}")
    private boolean eagerStartupLoad;

    public SwcbDebrisCollector(SwcbApiClient swcbApiClient,
                               DebrisStreamRepository debrisRepo,
                               ObjectMapper objectMapper) {
        this.swcbApiClient = swcbApiClient;
        this.debrisRepo = debrisRepo;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        if (!eagerStartupLoad) return;
        try {
            execute();
        } catch (Exception e) {
            log.warn("[SWCB_DEBRIS] Initial load failed: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${skypulse.swcb.schedule.debris-stream}")
    public CollectorResult collect() {
        return execute();
    }

    @Override
    protected String getSourceName() {
        return "SWCB_DEBRIS";
    }

    @Override
    protected Mono<List<SwcbDebrisRecord>> fetch() {
        return swcbApiClient.getRawJson("/webService/GetDebrisRainData.ashx")
                .map(json -> {
                    try {
                        return objectMapper.readValue(json,
                                objectMapper.getTypeFactory().constructCollectionType(
                                        List.class, SwcbDebrisRecord.class));
                    } catch (JsonProcessingException e) {
                        log.error("[SWCB_DEBRIS] Failed to parse response: {}", e.getMessage());
                        return Collections.<SwcbDebrisRecord>emptyList();
                    }
                });
    }

    @Override
    protected boolean validate(SwcbDebrisRecord item) {
        return item.DebrisNO() != null && !item.DebrisNO().isBlank()
                && item.AlertValue() != null;
    }

    @Override
    protected int persist(List<SwcbDebrisRecord> data) {
        // Preload existing rows once, then upsert by DebrisNO (avoids a select per record).
        Map<String, DebrisStream> existing = debrisRepo.findAll().stream()
                .collect(Collectors.toMap(DebrisStream::getDebrisNo, Function.identity()));

        List<DebrisStream> toSave = new ArrayList<>();
        for (var r : data) {
            DebrisStream d = existing.get(r.DebrisNO());
            if (d == null) {
                d = new DebrisStream();
                d.setDebrisNo(r.DebrisNO());
            }
            d.setCounty(r.County());
            d.setTown(r.Town());
            d.setVillage(r.Vill());
            d.setAlertValue(r.AlertValue());
            d.setRefStation1(r.STID1());
            d.setRefRatio1(r.STRT1());
            d.setRefStation2(r.STID2());
            d.setRefRatio2(r.STRT2());
            toSave.add(d);
        }

        if (!toSave.isEmpty()) {
            debrisRepo.saveAll(toSave);
            debrisRepo.flush();
        }
        return toSave.size();
    }
}
