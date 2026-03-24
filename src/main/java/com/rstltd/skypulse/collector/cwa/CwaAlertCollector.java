package com.rstltd.skypulse.collector.cwa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.cwa.dto.CwaAlertResponse;
import com.rstltd.skypulse.domain.alert.HazardAlert;
import com.rstltd.skypulse.repository.HazardAlertRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Component
public class CwaAlertCollector extends CollectorBase<CwaAlertResponse.AlertRecord> {

    private final CwaApiClient cwaApiClient;
    private final HazardAlertRepository alertRepo;
    private final ObjectMapper objectMapper;

    public CwaAlertCollector(CwaApiClient cwaApiClient,
                             HazardAlertRepository alertRepo,
                             ObjectMapper objectMapper) {
        this.cwaApiClient = cwaApiClient;
        this.alertRepo = alertRepo;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${skypulse.cwa.schedule.alert}")
    public CollectorResult collect() {
        return execute();
    }

    @Override
    protected String getSourceName() {
        return "CWA_ALERT";
    }

    @Override
    protected Mono<List<CwaAlertResponse.AlertRecord>> fetch() {
        return cwaApiClient.getDataset("W-C0033-002", CwaAlertResponse.class)
                .map(response -> {
                    if (response.result() != null && response.result().records() != null
                            && response.result().records().record() != null) {
                        return response.result().records().record();
                    }
                    return Collections.<CwaAlertResponse.AlertRecord>emptyList();
                });
    }

    @Override
    protected boolean validate(CwaAlertResponse.AlertRecord item) {
        return item.contentText() != null && !item.contentText().isBlank();
    }

    @Override
    protected int persist(List<CwaAlertResponse.AlertRecord> data) {
        int count = 0;
        for (var rec : data) {
            String sourceAlertId = buildAlertId(rec);
            if (sourceAlertId != null && alertRepo.findBySourceAlertId(sourceAlertId).isPresent()) {
                continue;
            }
            HazardAlert entity = mapToEntity(rec, sourceAlertId);
            alertRepo.save(entity);
            count++;
        }
        if (count > 0) alertRepo.flush();
        return count;
    }

    private String buildAlertId(CwaAlertResponse.AlertRecord rec) {
        if (rec.issueTime() != null && rec.phenomena() != null) {
            return "CWA-" + rec.phenomena() + "-" + rec.issueTime();
        }
        return null;
    }

    private HazardAlert mapToEntity(CwaAlertResponse.AlertRecord rec, String sourceAlertId) {
        HazardAlert entity = new HazardAlert();
        if (rec.issueTime() != null) {
            entity.setAlertTime(TimeUtils.toUtcOffset(
                    TimeUtils.parseIsoOffset(rec.issueTime())));
        } else {
            entity.setAlertTime(TimeUtils.nowUtc());
        }
        entity.setAlertType(rec.phenomena() != null ? rec.phenomena() : "WEATHER_WARNING");
        entity.setSeverity(rec.significance() != null ? rec.significance() : "WARNING");
        entity.setSource("CWA");
        entity.setSourceAlertId(sourceAlertId);
        entity.setTitle(rec.datasetDescription());
        entity.setDescription(rec.contentText());
        entity.setAffectedArea(rec.locationName());
        if (rec.endTime() != null) {
            try {
                entity.setExpiresAt(TimeUtils.toUtcOffset(
                        TimeUtils.parseIsoOffset(rec.endTime())));
            } catch (Exception e) {
                // endTime may not always be parseable
            }
        }
        try {
            entity.setRawData(objectMapper.writeValueAsString(rec));
        } catch (JsonProcessingException e) {
            log.warn("[CWA_ALERT] Failed to serialize raw data");
        }
        return entity;
    }
}
