package com.rstltd.skypulse.collector.swpc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One horizon entry of SWPC products/noaa-scales.json. The top-level document is a map keyed by
 * day offset ("0" = observed/current, "1".."3" = predicted days, "-1" = past). Each entry carries
 * a G/R/S block; only {@code Scale} is needed here (0..5, or null for predicted R/S which report
 * probabilities instead).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SwpcNoaaScalesEntry(
        String DateStamp,
        String TimeStamp,
        Scale G,
        Scale R,
        Scale S
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Scale(String Scale) {}
}
