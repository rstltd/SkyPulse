package com.rstltd.skypulse.util;

/**
 * Normalizes CWA shindo intensity labels to a 0..9 rank for sorting / filtering.
 * <p>
 * CWA 10-level scale (since 2020): 0級,1級,2級,3級,4級,5弱,5強,6弱,6強,7級.
 * Legacy non-split "5級"/"6級" map to the lower bound of the split (5弱 / 6弱).
 */
public final class SeismicIntensity {

    private SeismicIntensity() {}

    /** @return rank 0..9, or {@code null} if the label is unrecognized / blank. */
    public static Short rankOf(String label) {
        if (label == null) return null;
        String t = label.trim();
        if (t.isEmpty()) return null;
        char c = t.charAt(0);
        return switch (c) {
            case '0' -> (short) 0;
            case '1' -> (short) 1;
            case '2' -> (short) 2;
            case '3' -> (short) 3;
            case '4' -> (short) 4;
            case '5' -> t.contains("強") ? (short) 6 : (short) 5; // 5強=6, 5弱/5級=5
            case '6' -> t.contains("強") ? (short) 8 : (short) 7; // 6強=8, 6弱/6級=7
            case '7' -> (short) 9;
            default -> null;
        };
    }
}
