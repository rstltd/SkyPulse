package com.rstltd.skypulse.collector.cwa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.cwa.dto.CwaAlertResponse;
import com.rstltd.skypulse.collector.cwa.dto.CwaAlertResponse.*;
import com.rstltd.skypulse.domain.alert.HazardAlert;
import com.rstltd.skypulse.repository.HazardAlertRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
                    if (response.records() != null && response.records().record() != null) {
                        return response.records().record();
                    }
                    return Collections.<CwaAlertResponse.AlertRecord>emptyList();
                });
    }

    @Override
    protected boolean validate(CwaAlertResponse.AlertRecord item) {
        return item.contents() != null
                && item.contents().content() != null
                && item.contents().content().contentText() != null
                && !item.contents().content().contentText().isBlank();
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

    private String buildAlertId(AlertRecord rec) {
        String issueTime = extractIssueTime(rec);
        String phenomena = extractPhenomena(rec);
        if (issueTime != null && phenomena != null) {
            return "CWA-" + phenomena + "-" + issueTime;
        }
        return null;
    }

    private HazardAlert mapToEntity(AlertRecord rec, String sourceAlertId) {
        HazardAlert entity = new HazardAlert();

        String issueTime = extractIssueTime(rec);
        if (issueTime != null) {
            try {
                entity.setAlertTime(TimeUtils.toUtcOffset(
                        TimeUtils.parseCwaTimestamp(issueTime)));
            } catch (Exception e) {
                entity.setAlertTime(TimeUtils.nowUtc());
            }
        } else {
            entity.setAlertTime(TimeUtils.nowUtc());
        }

        entity.setAlertType(extractPhenomena(rec) != null ? extractPhenomena(rec) : "WEATHER_WARNING");
        entity.setSeverity(extractSignificance(rec) != null ? extractSignificance(rec) : "WARNING");
        entity.setSource("CWA");
        entity.setSourceAlertId(sourceAlertId);
        entity.setTitle(extractDatasetDescription(rec));
        entity.setDescription(rec.contents().content().contentText().trim());
        entity.setAffectedArea(extractLocationNames(rec));

        String endTime = extractEndTime(rec);
        if (endTime != null) {
            try {
                entity.setExpiresAt(TimeUtils.toUtcOffset(
                        TimeUtils.parseCwaTimestamp(endTime)));
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

    private String extractIssueTime(AlertRecord rec) {
        return rec.datasetInfo() != null ? rec.datasetInfo().issueTime() : null;
    }

    private String extractEndTime(AlertRecord rec) {
        if (rec.datasetInfo() != null && rec.datasetInfo().validTime() != null) {
            return rec.datasetInfo().validTime().endTime();
        }
        return null;
    }

    private String extractDatasetDescription(AlertRecord rec) {
        return rec.datasetInfo() != null ? rec.datasetInfo().datasetDescription() : null;
    }

    private String extractPhenomena(AlertRecord rec) {
        HazardInfo info = extractFirstHazardInfo(rec);
        return info != null ? info.phenomena() : null;
    }

    private String extractSignificance(AlertRecord rec) {
        HazardInfo info = extractFirstHazardInfo(rec);
        return info != null ? info.significance() : null;
    }

    private HazardInfo extractFirstHazardInfo(AlertRecord rec) {
        if (rec.hazardConditions() != null
                && rec.hazardConditions().hazards() != null
                && rec.hazardConditions().hazards().hazard() != null
                && !rec.hazardConditions().hazards().hazard().isEmpty()) {
            return rec.hazardConditions().hazards().hazard().get(0).info();
        }
        return null;
    }

    private String extractLocationNames(AlertRecord rec) {
        HazardInfo info = extractFirstHazardInfo(rec);
        if (info != null && info.affectedAreas() != null && info.affectedAreas().location() != null) {
            return info.affectedAreas().location().stream()
                    .map(Location::locationName)
                    .collect(Collectors.joining(", "));
        }
        return null;
    }
}
