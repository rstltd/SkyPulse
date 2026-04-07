package com.rstltd.skypulse.api.dto;

import com.rstltd.skypulse.service.SystemLogService;

import java.util.List;
import java.util.Map;

public record LogStatsResponse(
        Map<String, Long> byLevel,
        Map<String, Long> bySource,
        List<SystemLogService.HourlyCount> hourly
) {
    public static LogStatsResponse from(SystemLogService.LogStats stats) {
        return new LogStatsResponse(stats.byLevel(), stats.bySource(), stats.hourly());
    }
}
