package com.rstltd.skypulse.collector.cwa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CwaForecastResponse(
        String success,
        Result result
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("resource_id") String resourceId,
            Records records
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Records(List<Locations> Locations) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Locations(
            String DatasetDescription,
            String LocationsName,
            List<Location> Location
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(
            String LocationName,
            String Geocode,
            String Latitude,
            String Longitude,
            List<WeatherElement> WeatherElement
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WeatherElement(
            String ElementName,
            List<TimeEntry> Time
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TimeEntry(
            String StartTime,
            String EndTime,
            List<ElementValue> ElementValue
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ElementValue(
            String Temperature,
            String MaxTemperature,
            String MinTemperature,
            String Weather,
            String WeatherCode,
            String ProbabilityOfPrecipitation,
            String RelativeHumidity,
            String WindSpeed,
            String WindDirection,
            String BeaufortScale,
            String UVIndex
    ) {}
}
