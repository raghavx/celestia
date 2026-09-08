package com.celestia.core.lordage;

import com.celestia.ephemeris.Graha;

/**
 * The 27 nakshatras, each spanning exactly 13&deg;20' (= 800') from 0&deg; Aries.
 *
 * <p>Each nakshatra's lord is the Vimshottari lord for its position in the
 * 9-graha cycle, repeated three times (Ashwini &rarr; Ketu, Bharani &rarr; Venus,
 * &hellip;, Magha &rarr; Ketu again, Mula &rarr; Ketu). Source:
 * {@code core/REFERENCES.md}.
 */
public enum Nakshatra {
    ASHWINI,
    BHARANI,
    KRITTIKA,
    ROHINI,
    MRIGASHIRA,
    ARDRA,
    PUNARVASU,
    PUSHYA,
    ASHLESHA,
    MAGHA,
    PURVA_PHALGUNI,
    UTTARA_PHALGUNI,
    HASTA,
    CHITRA,
    SWATI,
    VISHAKHA,
    ANURADHA,
    JYESHTHA,
    MULA,
    PURVA_ASHADHA,
    UTTARA_ASHADHA,
    SHRAVANA,
    DHANISHTA,
    SHATABHISHA,
    PURVA_BHADRAPADA,
    UTTARA_BHADRAPADA,
    REVATI;

    /** Width of every nakshatra, in degrees (13&deg;20'). */
    public static final double SPAN_DEGREES = 40.0 / 3.0;

    /** Width of every pada (quarter), in degrees (3&deg;20'). */
    public static final double PADA_DEGREES = 10.0 / 3.0;

    /** The Vimshottari (star) lord of this nakshatra. */
    public Graha lord() {
        return Graha.vimshottariOrder().get(ordinal() % 9);
    }

    /** Ecliptic longitude where this nakshatra begins, in degrees. */
    public double startLongitude() {
        // multiply the exact integer numerator first, divide once: a single
        // rounding, matching BigFraction.of(ordinal*40, 3).doubleValue()
        return ordinal() * 40.0 / 3.0;
    }

    /**
     * The nakshatra containing {@code longitude}, half-open {@code [start, end)}.
     *
     * <p>Classified against the 27 equal divisions of the circle directly
     * ({@code floor(longitude * 27 / 360)}), so it does not drift with a rounded
     * span constant. A longitude that is the {@code double} nearest an exact
     * boundary may fall on either side of it by up to one ULP; real planetary
     * positions never sit exactly on a boundary.
     *
     * @param longitude a normalised ecliptic longitude in {@code [0, 360)}
     */
    public static Nakshatra at(double longitude) {
        int index = (int) (longitude * 27.0 / 360.0);
        return values()[Math.floorMod(index, 27)];
    }

    /**
     * Pada (1-4) of {@code longitude} within its nakshatra, half-open.
     *
     * @param longitude a normalised ecliptic longitude in {@code [0, 360)}
     */
    public static int padaAt(double longitude) {
        int quarter = (int) (longitude * 108.0 / 360.0); // 27 nakshatras * 4 padas
        return Math.floorMod(quarter, 4) + 1;
    }
}
