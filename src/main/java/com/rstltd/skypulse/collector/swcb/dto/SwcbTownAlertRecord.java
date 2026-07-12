package com.rstltd.skypulse.collector.swcb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** One row of SWCB GetCountyTownAlertValueList: township-level R70 alert baseline (mm). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SwcbTownAlertRecord(
        String County,
        String Town,
        BigDecimal AlertValue
) {}
