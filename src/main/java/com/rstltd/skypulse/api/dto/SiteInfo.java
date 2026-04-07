package com.rstltd.skypulse.api.dto;

public record SiteInfo(
        String siteId,
        String name,
        double latitude,
        double longitude,
        String associatedStationCode
) {}
