package com.rstltd.skypulse.collector.cwa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CwaEarthquakeResponse(
        String success,
        Result result
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("resource_id") String resourceId,
            Records records
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Records(
            String datasetDescription,
            List<Earthquake> Earthquake
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Earthquake(
            int EarthquakeNo,
            String ReportType,
            String ReportColor,
            String ReportContent,
            EarthquakeInfo EarthquakeInfo
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EarthquakeInfo(
            String OriginTime,
            String Source,
            double FocalDepth,
            Epicenter Epicenter,
            Magnitude EarthquakeMagnitude
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Epicenter(
            String Location,
            double EpicenterLatitude,
            double EpicenterLongitude
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Magnitude(
            String MagnitudeType,
            double MagnitudeValue
    ) {}
}
