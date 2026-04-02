package com.rstltd.skypulse.collector.cwa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CwaRainfallResponse(
        String success,
        Records records
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Records(List<Station> Station) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Station(
            String StationName,
            String StationId,
            ObsTime ObsTime,
            GeoInfo GeoInfo,
            RainfallElement RainfallElement
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ObsTime(String DateTime) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeoInfo(
            List<Coordinate> Coordinates,
            String StationAltitude,
            String CountyName,
            String TownName
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Coordinate(
            String CoordinateName,
            String StationLatitude,
            String StationLongitude
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RainfallElement(
            PrecipValue Now,
            PrecipValue Past10Min,
            PrecipValue Past1hr,
            PrecipValue Past3hr,
            PrecipValue Past6Hr,
            PrecipValue Past12hr,
            PrecipValue Past24hr
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrecipValue(String Precipitation) {}
}
