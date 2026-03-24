package com.rstltd.skypulse.service;

import com.rstltd.skypulse.api.dto.GnssQualityLevel;
import com.rstltd.skypulse.api.dto.GnssQualityResponse;
import com.rstltd.skypulse.domain.spaceweather.DstIndexRecord;
import com.rstltd.skypulse.domain.spaceweather.KpIndexRecord;
import com.rstltd.skypulse.domain.spaceweather.SolarWindRecord;
import com.rstltd.skypulse.domain.spaceweather.SpaceWeatherAlert;
import com.rstltd.skypulse.repository.DstIndexRecordRepository;
import com.rstltd.skypulse.repository.KpIndexRecordRepository;
import com.rstltd.skypulse.repository.SolarWindRecordRepository;
import com.rstltd.skypulse.repository.SpaceWeatherAlertRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class SpaceWeatherService {

    private final KpIndexRecordRepository kpRepo;
    private final DstIndexRecordRepository dstRepo;
    private final SolarWindRecordRepository solarWindRepo;
    private final SpaceWeatherAlertRepository alertRepo;

    public SpaceWeatherService(KpIndexRecordRepository kpRepo,
                               DstIndexRecordRepository dstRepo,
                               SolarWindRecordRepository solarWindRepo,
                               SpaceWeatherAlertRepository alertRepo) {
        this.kpRepo = kpRepo;
        this.dstRepo = dstRepo;
        this.solarWindRepo = solarWindRepo;
        this.alertRepo = alertRepo;
    }

    public Optional<KpIndexRecord> getCurrentKp() {
        return kpRepo.findTopByOrderByTimeDesc();
    }

    public List<KpIndexRecord> getKpHistory(int hours) {
        OffsetDateTime now = TimeUtils.nowUtc();
        return kpRepo.findByTimeBetweenOrderByTimeDesc(now.minusHours(hours), now);
    }

    public Page<KpIndexRecord> getKpHistoryPaged(int hours, Pageable pageable) {
        OffsetDateTime now = TimeUtils.nowUtc();
        return kpRepo.findByTimeBetweenOrderByTimeDesc(now.minusHours(hours), now, pageable);
    }

    public Optional<DstIndexRecord> getCurrentDst() {
        return dstRepo.findTopByOrderByTimeDesc();
    }

    public List<DstIndexRecord> getDstHistory(int hours) {
        OffsetDateTime now = TimeUtils.nowUtc();
        return dstRepo.findByTimeBetweenOrderByTimeDesc(now.minusHours(hours), now);
    }

    public Page<DstIndexRecord> getDstHistoryPaged(int hours, Pageable pageable) {
        OffsetDateTime now = TimeUtils.nowUtc();
        return dstRepo.findByTimeBetweenOrderByTimeDesc(now.minusHours(hours), now, pageable);
    }

    public Optional<SolarWindRecord> getCurrentSolarWind() {
        return solarWindRepo.findTopByOrderByTimeDesc();
    }

    public List<SpaceWeatherAlert> getRecentAlerts() {
        OffsetDateTime now = TimeUtils.nowUtc();
        return alertRepo.findByAlertTimeAfterOrderByAlertTimeDesc(now.minusDays(3));
    }

    public GnssQualityResponse assessGnssQuality() {
        Optional<KpIndexRecord> kpOpt = getCurrentKp();
        Optional<DstIndexRecord> dstOpt = getCurrentDst();
        Optional<SolarWindRecord> swOpt = getCurrentSolarWind();

        BigDecimal kp = kpOpt.map(KpIndexRecord::getKpValue).orElse(null);
        BigDecimal dst = dstOpt.map(DstIndexRecord::getDstValue).orElse(null);
        BigDecimal bz = swOpt.map(SolarWindRecord::getBz).orElse(null);
        BigDecimal windSpeed = swOpt.map(SolarWindRecord::getWindSpeed).orElse(null);

        // Find max G scale from recent alerts
        Integer gScale = getMaxGScale();
        Integer rScale = getMaxRScale();
        Integer sScale = getMaxSScale();

        GnssQualityLevel level = classifyQuality(kp, dst, gScale);
        String assessment = buildAssessment(kp, dst, gScale, level);
        String recommendation = switch (level) {
            case NORMAL -> "NORMAL";
            case CAUTION -> "MONITOR";
            case DEGRADED, SEVERE -> "FLAG_DISPLACEMENT_DATA";
        };

        return new GnssQualityResponse(
                TimeUtils.nowUtc(), level,
                kp, dst, bz, windSpeed,
                gScale, rScale, sScale,
                assessment, recommendation);
    }

    GnssQualityLevel classifyQuality(BigDecimal kp, BigDecimal dst, Integer gScale) {
        double kpVal = kp != null ? kp.doubleValue() : 0;
        double dstVal = dst != null ? dst.doubleValue() : 0;
        int g = gScale != null ? gScale : 0;

        if (kpVal > 7 || dstVal < -100 || g >= 3) return GnssQualityLevel.SEVERE;
        if (kpVal >= 5 || dstVal <= -50 || g >= 2) return GnssQualityLevel.DEGRADED;
        if (kpVal >= 4 || dstVal <= -30) return GnssQualityLevel.CAUTION;
        return GnssQualityLevel.NORMAL;
    }

    private String buildAssessment(BigDecimal kp, BigDecimal dst, Integer gScale,
                                    GnssQualityLevel level) {
        if (kp == null && dst == null) return "No space weather data available.";
        StringBuilder sb = new StringBuilder();
        if (kp != null) sb.append("Kp=").append(kp);
        if (dst != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("Dst=").append(dst).append(" nT");
        }
        if (gScale != null && gScale > 0) sb.append(", G").append(gScale);
        sb.append(". ");
        sb.append(switch (level) {
            case NORMAL -> "Geomagnetic conditions normal.";
            case CAUTION -> "Minor geomagnetic disturbance, monitor GNSS data quality.";
            case DEGRADED -> "Moderate geomagnetic storm, GNSS displacement data may be affected.";
            case SEVERE -> "Severe geomagnetic storm, GNSS data unreliable.";
        });
        return sb.toString();
    }

    private Integer getMaxGScale() {
        return getRecentAlerts().stream()
                .map(SpaceWeatherAlert::getGScale)
                .filter(g -> g != null)
                .max(Integer::compareTo)
                .orElse(null);
    }

    private Integer getMaxRScale() {
        return getRecentAlerts().stream()
                .map(SpaceWeatherAlert::getRScale)
                .filter(r -> r != null)
                .max(Integer::compareTo)
                .orElse(null);
    }

    private Integer getMaxSScale() {
        return getRecentAlerts().stream()
                .map(SpaceWeatherAlert::getSScale)
                .filter(s -> s != null)
                .max(Integer::compareTo)
                .orElse(null);
    }
}
