package com.rstltd.skypulse.collector.swpc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SwpcSolarWindSummary(
        @JsonProperty("time_tag") String timeTag,
        @JsonProperty("proton_speed") Double protonSpeed,
        @JsonProperty("bt") Double bt,
        @JsonProperty("bz_gsm") Double bzGsm
) {}
