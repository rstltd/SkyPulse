package com.rstltd.skypulse.collector.swpc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SwpcSolarWindSummary(
        @JsonProperty("TimeStamp") String timeStamp,
        @JsonProperty("WindSpeed") String windSpeed,
        @JsonProperty("Bt") String bt,
        @JsonProperty("Bz") String bz
) {}
