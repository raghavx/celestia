package com.celestia.core.lordage;

import com.celestia.ephemeris.Graha;

/**
 * The twelve zodiac signs, 30 degrees each, with their ruling graha.
 *
 * <p>Rulerships are the classical seven-planet scheme used by KP (no outer-planet
 * co-rulers). Source: {@code core/REFERENCES.md}.
 */
public enum Sign {
    ARIES(Graha.MARS),
    TAURUS(Graha.VENUS),
    GEMINI(Graha.MERCURY),
    CANCER(Graha.MOON),
    LEO(Graha.SUN),
    VIRGO(Graha.MERCURY),
    LIBRA(Graha.VENUS),
    SCORPIO(Graha.MARS),
    SAGITTARIUS(Graha.JUPITER),
    CAPRICORN(Graha.SATURN),
    AQUARIUS(Graha.SATURN),
    PISCES(Graha.JUPITER);

    /** Width of every sign, in degrees. */
    public static final double SPAN_DEGREES = 30.0;

    private final Graha lord;

    Sign(Graha lord) {
        this.lord = lord;
    }

    /** The ruling graha of this sign. */
    public Graha lord() {
        return lord;
    }

    /** Ecliptic longitude where this sign begins, in degrees. */
    public double startLongitude() {
        return ordinal() * SPAN_DEGREES;
    }

    /**
     * The sign containing {@code longitude}, half-open {@code [start, end)}.
     *
     * @param longitude a normalised ecliptic longitude in {@code [0, 360)}
     */
    public static Sign at(double longitude) {
        int index = (int) (longitude * 12.0 / 360.0);
        return values()[Math.floorMod(index, 12)];
    }
}
