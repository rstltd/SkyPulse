package com.rstltd.skypulse.api.dto.context;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Recent seismic activity near the query point. {@code strongestNearby} is the largest-magnitude
 * event within the quake radius over the lookback {@code window}; {@code nearbyCount} is how many
 * events fell in that radius/window. Estimating the intensity AT the query point needs a ground-
 * motion attenuation model — deferred (a future {@code estimatedLocalIntensity} field).
 */
public record SeismicContext(
        Event strongestNearby,
        int nearbyCount,
        String window,
        Provenance provenance,
        Freshness freshness
) {
    public record Event(
            String eventId,
            OffsetDateTime time,
            BigDecimal magnitude,
            BigDecimal depthKm,
            BigDecimal epicenterLat,
            BigDecimal epicenterLon,
            BigDecimal distanceKm,
            String maxIntensity,
            String locationDesc,
            String source
    ) {}
}
