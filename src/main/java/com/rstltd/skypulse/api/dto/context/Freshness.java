package com.rstltd.skypulse.api.dto.context;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * How current a domain block's data is. {@code stale} is true when the data is older than the
 * per-domain SLA ({@code expectedMaxAgeSeconds}).
 */
public record Freshness(
        OffsetDateTime observedAt,
        Long ageSeconds,
        Boolean stale,
        Long expectedMaxAgeSeconds
) {
    /** Build from the observation time, "now", and the domain SLA. */
    public static Freshness of(OffsetDateTime observedAt, OffsetDateTime now, long expectedMaxAgeSeconds) {
        if (observedAt == null) {
            return new Freshness(null, null, true, expectedMaxAgeSeconds);
        }
        long age = Math.max(0, Duration.between(observedAt, now).getSeconds());
        return new Freshness(observedAt, age, age > expectedMaxAgeSeconds, expectedMaxAgeSeconds);
    }
}
