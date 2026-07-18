package com.rstltd.skypulse.api.dto.context;

import java.util.List;

/**
 * Transparency/debug view: which station each coordinate-dependent domain WOULD use (provenance +
 * distance) without computing any indicators. Seismic is point-centred (no station) and GNSS is
 * global, so they are omitted here.
 */
public record CoverageResponse(
        QueryEcho query,
        LocationInfo location,
        Provenance rainfall,
        Provenance waterLevel,
        List<Warning> warnings
) {}
