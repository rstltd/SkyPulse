package com.rstltd.skypulse.api.dto.context;

/**
 * A non-fatal reason a domain block is null / partial. {@code code} is one of
 * NO_STATION_IN_RADIUS / STALE_DATA / OUTSIDE_COVERAGE / NO_BASELINE_FOR_TOWNSHIP.
 * Consumers must tolerate unknown codes (additive contract). {@code searchedRadiusKm} is set
 * only for NO_STATION_IN_RADIUS.
 */
public record Warning(
        String domain,
        String code,
        String message,
        Double searchedRadiusKm
) {
    public static Warning of(String domain, String code, String message) {
        return new Warning(domain, code, message, null);
    }

    public static Warning noStation(String domain, double searchedRadiusKm) {
        return new Warning(domain, "NO_STATION_IN_RADIUS",
                "No " + domain + " station within " + searchedRadiusKm + " km", searchedRadiusKm);
    }
}
