package com.rstltd.skypulse.collector.cwa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CwaEarthquakeResponse(
        String success,
        Records records
) {

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
            EarthquakeInfo EarthquakeInfo,
            Intensity Intensity
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Intensity(
            List<ShakingArea> ShakingArea
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShakingArea(
            String AreaDesc,
            String CountyName,
            String AreaIntensity,
            List<EqStation> EqStation
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EqStation(
            String StationName,
            String StationID,
            String SeismicIntensity,
            Double StationLatitude,
            Double StationLongitude,
            PgaPgv pga,
            PgaPgv pgv
    ) {}

    /** Shared shape for the CWA {@code pga} (gal) and {@code pgv} (kine=cm/s) blocks. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PgaPgv(
            String unit,
            Double EWComponent,
            Double NSComponent,
            Double VComponent,
            Double IntScaleValue
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
