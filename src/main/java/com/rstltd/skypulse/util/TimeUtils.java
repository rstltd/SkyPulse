package com.rstltd.skypulse.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class TimeUtils {

    private TimeUtils() {}

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    private static final DateTimeFormatter CWA_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter SWPC_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]");

    /**
     * Parse CWA local time string (Asia/Taipei) to Instant (UTC).
     * CWA returns timestamps like "2024-01-15 08:00:00".
     */
    public static Instant parseCwaTimestamp(String timestamp) {
        String ts = timestamp.trim();
        // Newer CWA feeds (e.g. earthquake OriginTime) use ISO-8601 with an explicit
        // offset like "2026-07-08T23:47:21+08:00"; honour the offset when present.
        try {
            return OffsetDateTime.parse(ts).toInstant();
        } catch (DateTimeParseException ignored) {
            // No offset present — fall through and treat as Asia/Taipei local time.
        }
        LocalDateTime local;
        try {
            local = LocalDateTime.parse(ts);             // ISO local "2026-07-08T23:47:21"
        } catch (DateTimeParseException e) {
            local = LocalDateTime.parse(ts, CWA_FORMAT); // legacy "2026-07-08 23:47:21"
        }
        return local.atZone(TAIPEI).toInstant();
    }

    /**
     * Parse ISO-8601 string to Instant.
     * Works for USGS and SWPC formats (e.g. "2024-01-15T08:00:00.000Z").
     */
    public static Instant parseIso(String timestamp) {
        return Instant.parse(timestamp);
    }

    /**
     * Parse ISO-8601 string with offset to Instant.
     * CWA observation stations use this format: "2026-03-24T12:00:00+08:00".
     */
    public static Instant parseIsoOffset(String timestamp) {
        return OffsetDateTime.parse(timestamp).toInstant();
    }

    /**
     * Parse SWPC UTC timestamp to Instant.
     * SWPC uses "yyyy-MM-dd HH:mm:ss.SSS" or "yyyy-MM-dd HH:mm:ss" (always UTC).
     */
    public static Instant parseSwpcTimestamp(String timestamp) {
        LocalDateTime local = LocalDateTime.parse(timestamp.trim(), SWPC_FORMAT);
        return local.toInstant(ZoneOffset.UTC);
    }

    /**
     * Parse an ISO-8601 SWPC timestamp that may carry a trailing 'Z' or offset
     * (e.g. "2026-07-11T15:59:00Z"), falling back to offsetless UTC.
     */
    public static Instant parseSwpcIso(String timestamp) {
        String ts = timestamp.trim();
        try {
            return Instant.parse(ts);                    // handles trailing 'Z' / offset
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(ts).toInstant(ZoneOffset.UTC); // offsetless → UTC
        }
    }

    /**
     * Parse WRA timestamp (Asia/Taipei, no offset) to Instant.
     * WRA uses ISO local format: "2026-03-24T13:10:00".
     */
    public static Instant parseWraTimestamp(String timestamp) {
        LocalDateTime local = LocalDateTime.parse(timestamp.trim());
        return local.atZone(TAIPEI).toInstant();
    }

    /**
     * Parse epoch milliseconds to Instant.
     * USGS uses this for earthquake times.
     */
    public static Instant fromEpochMillis(long millis) {
        return Instant.ofEpochMilli(millis);
    }

    /**
     * Convert Instant to OffsetDateTime in UTC (for JPA TIMESTAMPTZ columns).
     */
    public static OffsetDateTime toUtcOffset(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    /**
     * Get current UTC time as OffsetDateTime.
     */
    public static OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
