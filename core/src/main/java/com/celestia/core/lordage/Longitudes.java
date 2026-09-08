package com.celestia.core.lordage;

/**
 * Ecliptic-longitude helpers shared by the KP decomposition.
 *
 * <p><b>Boundary convention</b> (FR-011): every division of the zodiac -- sign,
 * nakshatra, pada, sub, sub-sub -- is a half-open interval {@code [start, end)}.
 * A longitude that lands exactly on a boundary belongs to the division of
 * <i>higher</i> longitude. {@code chainFor(0.0)} is therefore Aries / Ashwini /
 * pada 1.
 */
public final class Longitudes {

    public static final double FULL_CIRCLE = 360.0;

    private Longitudes() {}

    /**
     * Reduce any finite longitude into {@code [0, 360)}.
     *
     * @throws IllegalArgumentException if {@code degrees} is NaN or infinite
     */
    public static double normalize(double degrees) {
        if (!Double.isFinite(degrees)) {
            throw new IllegalArgumentException("longitude must be finite: " + degrees);
        }
        double r = degrees % FULL_CIRCLE;
        if (r < 0) {
            r += FULL_CIRCLE;
        }
        // -0.0, or a tiny negative that rounded to exactly 360.0
        return r == FULL_CIRCLE ? 0.0 : r + 0.0;
    }
}
