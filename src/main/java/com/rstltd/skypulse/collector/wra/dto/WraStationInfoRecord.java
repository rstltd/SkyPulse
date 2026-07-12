package com.rstltd.skypulse.collector.wra.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WraStationInfoRecord(
        String basinidentifier,
        String observatoryname,
        String rivername,
        String locationaddress,
        String observationstatus,
        String locationbytwd97_xy,
        String alertlevel1,
        String alertlevel2,
        String alertlevel3
) {}
