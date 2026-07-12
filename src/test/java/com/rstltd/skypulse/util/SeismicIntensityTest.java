package com.rstltd.skypulse.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SeismicIntensityTest {

    @ParameterizedTest
    @CsvSource({
            "0級,0", "1級,1", "2級,2", "3級,3", "4級,4",
            "5弱,5", "5強,6", "6弱,7", "6強,8", "7級,9",
            // legacy non-split scale maps to the lower bound
            "5級,5", "6級,7"
    })
    void rankOf_mapsCwaScale(String label, short expected) {
        assertEquals(expected, SeismicIntensity.rankOf(label));
    }

    @Test
    void rankOf_toleratesWhitespaceAndBareDigit() {
        assertEquals((short) 4, SeismicIntensity.rankOf(" 4級 "));
        assertEquals((short) 9, SeismicIntensity.rankOf("7")); // bare "7" == 7級 == rank 9
    }

    @Test
    void rankOf_nullOrUnknown_isNull() {
        assertNull(SeismicIntensity.rankOf(null));
        assertNull(SeismicIntensity.rankOf(""));
        assertNull(SeismicIntensity.rankOf("無感"));
    }
}
