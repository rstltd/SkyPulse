package com.rstltd.skypulse.collector.wra.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WraReservoirDailyRecord(
        String reservoiridentifier,
        String reservoirname,
        String nwlmax,
        String capacity
) {}
