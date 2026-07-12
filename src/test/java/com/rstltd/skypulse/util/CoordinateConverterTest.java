package com.rstltd.skypulse.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoordinateConverterTest {

    private static final double TOL = 1e-4; // ~11 m at this latitude — well within reprojection agreement

    @Test
    void twd97ToWgs84_falseEastingOrigin_isEquatorAtCentralMeridian() {
        // (x_0, 0) sits on the equator at the central meridian (lon 121) by construction.
        var ll = CoordinateConverter.twd97ToWgs84(250000.0, 0.0);
        assertEquals(0.0, ll.lat(), TOL);
        assertEquals(121.0, ll.lon(), TOL);
    }

    @Test
    void twd97ToWgs84_realWraStation_matchesGroundTruth() {
        // WRA station 1010H001 locationbytwd97_xy = "310928.30 2790168.45"
        // Independently computed (inverse transverse Mercator, GRS80): 25.218970, 121.604678.
        var ll = CoordinateConverter.twd97ToWgs84(310928.30, 2790168.45);
        assertEquals(25.218970, ll.lat(), TOL);
        assertEquals(121.604678, ll.lon(), TOL);
    }

    @Test
    void fromTwd97Xy_parsesSpaceSeparatedPair() {
        var ll = CoordinateConverter.fromTwd97Xy("310928.30 2790168.45");
        assertNotNull(ll);
        assertEquals(25.218970, ll.lat(), TOL);
        assertEquals(121.604678, ll.lon(), TOL);
    }

    @Test
    void fromTwd97Xy_blankOrMalformed_isNull() {
        assertNull(CoordinateConverter.fromTwd97Xy(null));
        assertNull(CoordinateConverter.fromTwd97Xy(""));
        assertNull(CoordinateConverter.fromTwd97Xy("   "));
        assertNull(CoordinateConverter.fromTwd97Xy("310928.30")); // single token
        assertNull(CoordinateConverter.fromTwd97Xy("abc def"));
    }

    @Test
    void fromTwd97Xy_outsideTaiwan_isNull() {
        // Origin (0,0) reprojects far from Taiwan -> rejected by the bounding-box guard.
        assertNull(CoordinateConverter.fromTwd97Xy("0 0"));
    }
}
