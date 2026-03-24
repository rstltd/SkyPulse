package com.rstltd.skypulse.collector.usgs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeoJsonResponse(
        String type,
        List<Feature> features
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feature(
            String type,
            String id,
            Properties properties,
            Geometry geometry
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(
            double mag,
            String place,
            long time,
            String magType,
            String type
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Geometry(
            String type,
            double[] coordinates
    ) {}
}
