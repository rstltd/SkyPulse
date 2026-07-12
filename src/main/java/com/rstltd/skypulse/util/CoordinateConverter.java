package com.rstltd.skypulse.util;

import org.locationtech.proj4j.BasicCoordinateTransform;
import org.locationtech.proj4j.CRSFactory;
import org.locationtech.proj4j.CoordinateReferenceSystem;
import org.locationtech.proj4j.ProjCoordinate;

/**
 * Reprojects Taiwan TWD97 / TM2 (EPSG:3826) easting/northing to WGS84 lat/lon, used to give WRA
 * water-level stations coordinates for the coordinate-query API. Defined via proj4 parameter
 * strings so no EPSG database dependency is needed; TWD97 and WGS84 share the GRS80 ellipsoid
 * with no datum shift (towgs84=0).
 */
public final class CoordinateConverter {

    private static final String TWD97_TM2 =
            "+proj=tmerc +lat_0=0 +lon_0=121 +k=0.9999 +x_0=250000 +y_0=0 "
            + "+ellps=GRS80 +towgs84=0,0,0,0,0,0,0 +units=m +no_defs";
    private static final String WGS84 = "+proj=longlat +datum=WGS84 +no_defs";

    private static final CoordinateReferenceSystem SRC;
    private static final CoordinateReferenceSystem DST;

    static {
        CRSFactory factory = new CRSFactory();
        SRC = factory.createFromParameters("TWD97-TM2", TWD97_TM2);
        DST = factory.createFromParameters("WGS84", WGS84);
    }

    private CoordinateConverter() {}

    public record LatLon(double lat, double lon) {}

    /**
     * @param easting  TWD97 TM2 easting (metres)
     * @param northing TWD97 TM2 northing (metres)
     * @return WGS84 latitude/longitude (degrees)
     */
    public static LatLon twd97ToWgs84(double easting, double northing) {
        // BasicCoordinateTransform is not thread-safe; create one per call (cheap).
        ProjCoordinate out = new ProjCoordinate();
        new BasicCoordinateTransform(SRC, DST)
                .transform(new ProjCoordinate(easting, northing), out);
        return new LatLon(out.y, out.x); // proj4j: x = lon, y = lat
    }

    /**
     * Parse a WRA {@code locationbytwd97_xy} value ("easting northing", space-separated) and
     * convert to WGS84. Returns {@code null} for blank / malformed / out-of-Taiwan input.
     */
    public static LatLon fromTwd97Xy(String xy) {
        if (xy == null || xy.isBlank()) return null;
        String[] parts = xy.trim().split("\\s+");
        if (parts.length != 2) return null;
        try {
            LatLon ll = twd97ToWgs84(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
            // Guard against garbage coordinates landing outside Taiwan.
            if (ll.lat() < 21.0 || ll.lat() > 26.5 || ll.lon() < 118.0 || ll.lon() > 123.0) {
                return null;
            }
            return ll;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
