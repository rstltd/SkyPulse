package com.rstltd.skypulse.service;

import com.rstltd.skypulse.collector.common.CollectorResult;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CollectorStatusService {

    private final ConcurrentHashMap<String, CollectorStatus> statuses = new ConcurrentHashMap<>();

    public void recordResult(CollectorResult result) {
        statuses.put(result.source(), new CollectorStatus(
                result.source(),
                result.status().name(),
                OffsetDateTime.now(ZoneOffset.UTC),
                result.persistedCount(),
                result.fetchedCount(),
                result.durationMs(),
                result.errorMessage()
        ));
    }

    public List<CollectorStatus> getAllStatuses() {
        return statuses.values().stream()
                .sorted(Comparator.comparing(CollectorStatus::source))
                .toList();
    }

    public record CollectorStatus(
            String source,
            String status,
            OffsetDateTime lastRunTime,
            int persistedCount,
            int fetchedCount,
            long durationMs,
            String errorMessage
    ) {}
}
