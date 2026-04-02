package com.rstltd.skypulse.collector.swpc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SwpcKpIndexRow(
        @JsonProperty("time_tag") String timeTag,
        @JsonProperty("Kp") Double kp
) {}
