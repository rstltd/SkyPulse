package com.rstltd.skypulse.service;

import com.rstltd.skypulse.domain.alert.HazardAlert;
import com.rstltd.skypulse.repository.HazardAlertRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AlertService {

    private final HazardAlertRepository alertRepo;

    public AlertService(HazardAlertRepository alertRepo) {
        this.alertRepo = alertRepo;
    }

    public List<HazardAlert> getActiveAlerts() {
        OffsetDateTime now = TimeUtils.nowUtc();
        return alertRepo.findByAlertTimeAfterOrderByAlertTimeDesc(now.minusHours(24));
    }

    public List<HazardAlert> getAlertsByTypeAndTime(String type, OffsetDateTime since) {
        if (type != null && since != null) {
            return alertRepo.findByAlertTypeAndAlertTimeBetween(type, since, TimeUtils.nowUtc());
        }
        if (type != null) {
            return alertRepo.findByAlertType(type);
        }
        if (since != null) {
            return alertRepo.findByAlertTimeAfterOrderByAlertTimeDesc(since);
        }
        return getActiveAlerts();
    }

    public Page<HazardAlert> getAlertsPaged(String type, OffsetDateTime since, Pageable pageable) {
        OffsetDateTime now = TimeUtils.nowUtc();
        if (type != null && since != null) {
            return alertRepo.findByAlertTypeAndAlertTimeBetween(type, since, now, pageable);
        }
        OffsetDateTime effectiveSince = since != null ? since : now.minusHours(24);
        return alertRepo.findByAlertTimeAfterOrderByAlertTimeDesc(effectiveSince, pageable);
    }
}
