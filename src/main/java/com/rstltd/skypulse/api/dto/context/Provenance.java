package com.rstltd.skypulse.api.dto.context;

import java.math.BigDecimal;

/**
 * Where a domain block's data came from (data-transparency envelope). {@code stationCode}/
 * {@code stationName}/{@code stationLat}/{@code stationLon}/{@code distanceKm} are null for
 * non-station domains (e.g. seismic uses the query point as centre).
 */
public record Provenance(
        String source,
        String dataset,
        String stationCode,
        String stationName,
        BigDecimal stationLat,
        BigDecimal stationLon,
        BigDecimal distanceKm
) {}
