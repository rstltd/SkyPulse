package com.rstltd.skypulse.collector.swcb.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * One row of SWCB GetDebrisRainData: a potential debris-flow stream with its R70 alert baseline
 * and up to two reference rainfall stations (STID/STName/STRT = code/name/blend ratio).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SwcbDebrisRecord(
        String County,
        String Town,
        String Vill,
        String DebrisNO,
        BigDecimal AlertValue,
        String STID1,
        String STName1,
        BigDecimal STRT1,
        String STID2,
        String STName2,
        BigDecimal STRT2
) {}
