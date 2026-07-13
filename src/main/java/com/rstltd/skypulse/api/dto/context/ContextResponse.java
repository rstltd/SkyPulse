package com.rstltd.skypulse.api.dto.context;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Integrated environmental context for a single coordinate (Direction B main product). The four
 * domain blocks are serialized even when null (global config is non_null, so they are explicitly
 * marked ALWAYS) — a null block plus a matching entry in {@code warnings} tells the consumer why.
 * {@code gnssQuality} is coordinate-independent and always present.
 */
public record ContextResponse(
        QueryEcho query,
        LocationInfo location,
        @JsonInclude(JsonInclude.Include.ALWAYS) RainfallContext rainfall,
        @JsonInclude(JsonInclude.Include.ALWAYS) SeismicContext seismic,
        GnssQualityContext gnssQuality,
        @JsonInclude(JsonInclude.Include.ALWAYS) WaterLevelContext waterLevel,
        List<Warning> warnings,
        ResponseMeta meta
) {}
