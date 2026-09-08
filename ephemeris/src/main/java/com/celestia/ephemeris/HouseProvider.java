package com.celestia.ephemeris;

import java.util.Map;

/**
 * Placidus house cusps and chart angles, sidereal (KP-New).
 *
 * <p>Contract (see
 * {@code specs/002-natal-chart-cusps/contracts/house-provider-api.md}):
 *
 * <ul>
 *   <li>{@link #houses(BirthData)} throws {@link PlacidusUndefinedException} when
 *       {@code |latitude| >= polarLimit} (default 66.0°) — before any backend call.</li>
 *   <li>{@link #anglesOnly(BirthData)} returns the Ascendant and Midheaven at any
 *       latitude below ±90°; it does not depend on the house system.</li>
 *   <li>Deterministic, thread-safe; no Swiss Ephemeris type in this API.</li>
 * </ul>
 */
public interface HouseProvider {

    HouseResult houses(BirthData birthData);

    Map<Angle, Double> anglesOnly(BirthData birthData);
}
