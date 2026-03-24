package com.rstltd.skypulse.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class TimeUtilsTest {

    @Test
    void parseCwaTimestamp_convertsFromTaipeiToUtc() {
        // 2024-01-15 08:00:00 Taipei (UTC+8) = 2024-01-15 00:00:00 UTC
        Instant result = TimeUtils.parseCwaTimestamp("2024-01-15 08:00:00");
        assertEquals(Instant.parse("2024-01-15T00:00:00Z"), result);
    }

    @Test
    void parseCwaTimestamp_midnightTaipei() {
        // 2024-01-15 00:00:00 Taipei = 2024-01-14 16:00:00 UTC
        Instant result = TimeUtils.parseCwaTimestamp("2024-01-15 00:00:00");
        assertEquals(Instant.parse("2024-01-14T16:00:00Z"), result);
    }

    @Test
    void parseIso_standardFormat() {
        Instant result = TimeUtils.parseIso("2024-01-15T08:00:00.000Z");
        assertEquals(Instant.parse("2024-01-15T08:00:00Z"), result);
    }

    @Test
    void fromEpochMillis_correctConversion() {
        // 2024-01-15T00:00:00Z = 1705276800000
        Instant result = TimeUtils.fromEpochMillis(1705276800000L);
        assertEquals(Instant.parse("2024-01-15T00:00:00Z"), result);
    }

    @Test
    void toUtcOffset_hasUtcZone() {
        Instant instant = Instant.parse("2024-01-15T08:00:00Z");
        OffsetDateTime result = TimeUtils.toUtcOffset(instant);
        assertEquals(ZoneOffset.UTC, result.getOffset());
        assertEquals(8, result.getHour());
    }

    @Test
    void nowUtc_isCloseToCurrentTime() {
        OffsetDateTime result = TimeUtils.nowUtc();
        assertEquals(ZoneOffset.UTC, result.getOffset());
        long diffMs = Math.abs(result.toInstant().toEpochMilli() - Instant.now().toEpochMilli());
        assertTrue(diffMs < 1000, "nowUtc should be within 1 second of Instant.now()");
    }
}
