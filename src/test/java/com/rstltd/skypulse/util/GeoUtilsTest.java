package com.rstltd.skypulse.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoUtilsTest {

    @Test
    void distanceKm_samePoint_returnsZero() {
        assertEquals(0.0, GeoUtils.distanceKm(25.0, 121.5, 25.0, 121.5), 0.001);
    }

    @Test
    void distanceKm_taipeiToKaohsiung() {
        // Taipei (25.033, 121.565) to Kaohsiung (22.627, 120.301)
        // Approximate distance: ~302 km
        double distance = GeoUtils.distanceKm(25.033, 121.565, 22.627, 120.301);
        assertEquals(302.0, distance, 10.0);
    }

    @Test
    void distanceKm_shortDistance() {
        // Two points ~14 km apart
        double distance = GeoUtils.distanceKm(25.0, 121.5, 25.1, 121.6);
        assertTrue(distance > 10 && distance < 20);
    }

    @Test
    void isInTaiwanRegion_taipei_returnsTrue() {
        assertTrue(GeoUtils.isInTaiwanRegion(25.033, 121.565));
    }

    @Test
    void isInTaiwanRegion_kaohsiung_returnsTrue() {
        assertTrue(GeoUtils.isInTaiwanRegion(22.627, 120.301));
    }

    @Test
    void isInTaiwanRegion_tokyo_returnsFalse() {
        assertFalse(GeoUtils.isInTaiwanRegion(35.68, 139.69));
    }

    @Test
    void isInTaiwanRegion_boundary_returnsTrue() {
        assertTrue(GeoUtils.isInTaiwanRegion(21.5, 119.0));
        assertTrue(GeoUtils.isInTaiwanRegion(25.5, 122.5));
    }

    @Test
    void isInTaiwanRegion_justOutside_returnsFalse() {
        assertFalse(GeoUtils.isInTaiwanRegion(21.4, 121.0));
        assertFalse(GeoUtils.isInTaiwanRegion(25.0, 122.6));
    }
}
