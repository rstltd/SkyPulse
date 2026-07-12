package com.rstltd.skypulse.collector.moenv.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One row of the MOENV GISEPA_P_27 reservoir water-quality station dataset. Used only to seed
 * reservoir coordinates: {@code dam} is the reservoir name (match key against the WRA reservoir
 * dimension), and {@code latitute}/{@code longitute} (sic — the API's field spelling) are already
 * WGS84.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MoenvReservoirRecord(
        String dam,
        String countyname,
        String latitute,
        String longitute
) {}
