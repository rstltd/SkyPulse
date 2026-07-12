package com.rstltd.skypulse.service;

import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.domain.log.LogCategory;
import com.rstltd.skypulse.domain.log.SystemLog;
import com.rstltd.skypulse.repository.SystemLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SystemLogService {

    private static final Logger log = LoggerFactory.getLogger(SystemLogService.class);

    private final SystemLogRepository logRepository;

    @Value("${skypulse.logging.retention-days:30}")
    private int retentionDays;

    public SystemLogService(SystemLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    public void recordCollectorResult(CollectorResult result) {
        try {
            SystemLog entry = new SystemLog();
            entry.setTime(OffsetDateTime.now(ZoneOffset.UTC));
            entry.setCategory(LogCategory.COLLECTOR.name());
            entry.setLevel(mapStatusToLevel(result.status()));
            entry.setSource(result.source());
            entry.setMessage(truncate(buildCollectorMessage(result), 500));
            entry.setFetchedCount(result.fetchedCount());
            entry.setValidCount(result.validCount());
            entry.setPersistedCount(result.persistedCount());
            entry.setDurationMs(result.durationMs());
            if (result.errorMessage() != null) {
                entry.setErrorDetail(result.errorMessage());
            }
            logRepository.save(entry);
        } catch (Exception e) {
            log.warn("[SYSTEM_LOG] Failed to persist collector result for {}: {}",
                    result.source(), e.getMessage());
        }
    }

    public void logEvent(String category, String level, String source, String message) {
        try {
            SystemLog entry = new SystemLog();
            entry.setTime(OffsetDateTime.now(ZoneOffset.UTC));
            entry.setCategory(category);
            entry.setLevel(level);
            entry.setSource(source);
            entry.setMessage(truncate(message, 500));
            logRepository.save(entry);
        } catch (Exception e) {
            log.warn("[SYSTEM_LOG] Failed to persist event {}/{}: {}",
                    category, source, e.getMessage());
        }
    }

    public void logError(String source, String message, Throwable ex) {
        try {
            SystemLog entry = new SystemLog();
            entry.setTime(OffsetDateTime.now(ZoneOffset.UTC));
            entry.setCategory(LogCategory.ERROR.name());
            entry.setLevel("ERROR");
            entry.setSource(source);
            entry.setMessage(truncate(message, 500));
            if (ex != null) {
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                entry.setErrorDetail(sw.toString());
            }
            logRepository.save(entry);
        } catch (Exception e) {
            log.warn("[SYSTEM_LOG] Failed to persist error for {}: {}",
                    source, e.getMessage());
        }
    }

    public Page<SystemLog> queryLogs(String category, String level, String source,
                                      String keyword, OffsetDateTime start,
                                      OffsetDateTime end, Pageable pageable) {
        if (start == null) {
            start = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        }
        if (end == null) {
            end = OffsetDateTime.now(ZoneOffset.UTC);
        }
        String keywordPattern = keyword != null ? "%" + keyword.toLowerCase() + "%" : null;
        return logRepository.findFiltered(start, end, category, level, source, keywordPattern, pageable);
    }

    public LogStats getStats(OffsetDateTime start, OffsetDateTime end) {
        if (start == null) {
            start = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        }
        if (end == null) {
            end = OffsetDateTime.now(ZoneOffset.UTC);
        }

        Map<String, Long> byLevel = logRepository.countByLevelBetween(start, end).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1],
                        (a, b) -> a,
                        LinkedHashMap::new));

        Map<String, Long> bySource = logRepository.countBySourceBetween(start, end).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1],
                        (a, b) -> a,
                        LinkedHashMap::new));

        List<HourlyCount> hourly = buildHourlyCounts(
                logRepository.countByHourAndLevel(start, end));

        return new LogStats(byLevel, bySource, hourly);
    }

    public List<String> getDistinctSources() {
        return logRepository.findDistinctSources();
    }

    @Scheduled(cron = "${skypulse.logging.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void cleanupOldLogs() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(retentionDays);
        int deleted = logRepository.deleteByTimeBefore(cutoff);
        if (deleted > 0) {
            log.info("[LOG_CLEANUP] Deleted {} logs older than {} days", deleted, retentionDays);
        }
    }

    private String mapStatusToLevel(CollectorResult.Status status) {
        return switch (status) {
            case SUCCESS -> "INFO";
            case EMPTY -> "WARN";
            case FAILURE -> "ERROR";
        };
    }

    private String buildCollectorMessage(CollectorResult result) {
        return switch (result.status()) {
            case SUCCESS -> String.format("Collected %d records (fetched=%d, valid=%d, %dms)",
                    result.persistedCount(), result.fetchedCount(), result.validCount(), result.durationMs());
            case EMPTY -> String.format("No data collected (%dms)", result.durationMs());
            case FAILURE -> String.format("Collection failed (%dms): %s",
                    result.durationMs(), result.errorMessage());
        };
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    private List<HourlyCount> buildHourlyCounts(List<Object[]> rows) {
        Map<String, HourlyCount> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String bucket = row[0].toString();
            String level = (String) row[1];
            long count = ((Number) row[2]).longValue();

            map.computeIfAbsent(bucket, k -> new HourlyCount(k, 0, 0, 0));
            HourlyCount hc = map.get(bucket);
            map.put(bucket, switch (level) {
                case "INFO" -> new HourlyCount(bucket, count, hc.warn(), hc.error());
                case "WARN" -> new HourlyCount(bucket, hc.info(), count, hc.error());
                case "ERROR" -> new HourlyCount(bucket, hc.info(), hc.warn(), count);
                default -> hc;
            });
        }
        return new ArrayList<>(map.values());
    }

    public record LogStats(
            Map<String, Long> byLevel,
            Map<String, Long> bySource,
            List<HourlyCount> hourly
    ) {}

    public record HourlyCount(String time, long info, long warn, long error) {}
}
