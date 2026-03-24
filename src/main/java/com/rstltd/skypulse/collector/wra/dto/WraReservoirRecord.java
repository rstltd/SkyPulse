package com.rstltd.skypulse.collector.wra.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WraReservoirRecord(
        String reservoiridentifier,
        String observationtime,
        String waterlevel,
        String effectivewaterstoragecapacity,
        String inflowdischarge,
        String totaloutflow,
        String accumulaterainfallincatchment
) {}
