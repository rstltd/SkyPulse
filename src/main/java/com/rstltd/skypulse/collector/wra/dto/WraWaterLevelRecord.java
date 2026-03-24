package com.rstltd.skypulse.collector.wra.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WraWaterLevelRecord(
        String stationid,
        String datetime,
        String waterlevel
) {}
