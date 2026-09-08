package com.celestia.core.chart;

import com.celestia.core.lordage.LordChain;
import com.celestia.ephemeris.Angle;

/**
 * A chart angle (Ascendant or Midheaven) with its longitude and lord chain.
 * {@code Angle} names which one; {@code AnglePoint} is the value.
 *
 * @param angle which angle
 * @param longitude sidereal ecliptic longitude, degrees in {@code [0, 360)}
 * @param lordChain the KP lord chain of this angle
 */
public record AnglePoint(Angle angle, double longitude, LordChain lordChain) {}
