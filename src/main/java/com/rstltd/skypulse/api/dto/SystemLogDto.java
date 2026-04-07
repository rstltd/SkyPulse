package com.rstltd.skypulse.api.dto;

import com.rstltd.skypulse.domain.log.SystemLog;

import java.time.OffsetDateTime;

public record SystemLogDto(
        OffsetDateTime time,
        String category,
        String level,
        String source,
        String message,
        Integer fetchedCount,
        Integer validCount,
        Integer persistedCount,
        Long durationMs,
        String errorDetail
) {
    public static SystemLogDto from(SystemLog entity) {
        return new SystemLogDto(
                entity.getTime(),
                entity.getCategory(),
                entity.getLevel(),
                entity.getSource(),
                entity.getMessage(),
                entity.getFetchedCount(),
                entity.getValidCount(),
                entity.getPersistedCount(),
                entity.getDurationMs(),
                entity.getErrorDetail()
        );
    }
}
