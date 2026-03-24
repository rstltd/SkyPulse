package com.rstltd.skypulse.collector.cwa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CwaWeatherResponse(
        String success,
        Result result
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("resource_id") String resourceId,
            Records records
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Records(List<Station> Station) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Station(
            String StationName,
            String StationId,
            ObsTime ObsTime,
            GeoInfo GeoInfo,
            WeatherElement WeatherElement
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
    public record WeatherElement(
            String Weather,
            PrecipValue Now,
            String WindDirection,
            String WindSpeed,
            String AirTemperature,
            String RelativeHumidity,
            String AirPressure
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrecipValue(String Precipitation) {}
}
