package com.celestia.core.chart;

import com.celestia.core.lordage.LordChain;
import com.celestia.ephemeris.Graha;

/**
 * One house cusp: its number, its sidereal longitude, and its full KP lordage.
 *
 * @param house 1..12
 * @param longitude sidereal ecliptic longitude, degrees in {@code [0, 360)}
 * @param lordChain the KP lord chain of this cusp
 */
public record Cusp(int house, double longitude, LordChain lordChain) {

    public Cusp {
        if (house < 1 || house > 12) {
            throw new IllegalArgumentException("house out of 1..12: " + house);
        }
    }

    /** The cuspal sub lord — the KP determinant of the matters of this house. */
    public Graha subLord() {
        return lordChain.subLord();
    }
}
