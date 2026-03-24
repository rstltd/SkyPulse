package com.rstltd.skypulse.collector.swpc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SwpcAlertEntry(
        @JsonProperty("product_id") String productId,
        @JsonProperty("issue_datetime") String issueDatetime,
        String message
) {}
