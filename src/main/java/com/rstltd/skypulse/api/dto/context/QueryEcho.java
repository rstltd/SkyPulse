package com.rstltd.skypulse.api.dto.context;

/** Echoes the resolved query parameters (including defaults filled in by the server). */
public record QueryEcho(
        double lat,
        double lon,
        Double radiusKm,
        double quakeRadiusKm,
        boolean autoRadius,
        double maxAutoRadiusKm
) {}
