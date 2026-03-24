package com.rstltd.skypulse.util;

public final class GeoUtils {

    private GeoUtils() {}

    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Haversine distance between two points in kilometers.
     */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Check if a point is within the Taiwan region bounding box.
     * lat 21.5-25.5, lon 119.0-122.5
     */
    public static boolean isInTaiwanRegion(double lat, double lon) {
        return lat >= 21.5 && lat <= 25.5 && lon >= 119.0 && lon <= 122.5;
    }
}
