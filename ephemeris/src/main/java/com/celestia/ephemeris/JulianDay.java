package com.celestia.ephemeris;

/**
 * A moment expressed on the two time scales the ephemeris needs.
 *
 * @param jdUt Julian Day, Universal Time
 * @param jdTt Julian Day, Terrestrial (Dynamical) Time
 * @param deltaTSeconds {@code jdTt - jdUt} expressed in seconds (the applied &Delta;T)
 */
public record JulianDay(double jdUt, double jdTt, double deltaTSeconds) {}
