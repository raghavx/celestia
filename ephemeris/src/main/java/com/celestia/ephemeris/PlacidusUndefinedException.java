package com.celestia.ephemeris;

/**
 * Thrown by {@code HouseProvider.houses(...)} when the birth latitude is at or
 * beyond the polar limit, where Placidus house cusps are mathematically
 * undefined (ADR-0004). The Ascendant and Midheaven remain available via
 * {@code anglesOnly(...)}.
 */
public class PlacidusUndefinedException extends EphemerisException {

    public PlacidusUndefinedException(double latitude, double limit) {
        super("Placidus cusps are undefined at latitude " + latitude
                + " (polar limit " + limit + "); use anglesOnly() for the Ascendant/Midheaven");
    }
}
