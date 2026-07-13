package com.rstltd.skypulse.api.dto.context;

/**
 * County/township of the query point, taken from the nearest station used for the rainfall/water
 * blocks. {@code inTaiwan} is false for valid coordinates outside the Taiwan coverage bbox.
 */
public record LocationInfo(
        String county,
        String township,
        boolean inTaiwan
) {}
