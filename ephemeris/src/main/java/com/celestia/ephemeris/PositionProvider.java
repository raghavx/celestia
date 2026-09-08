package com.celestia.ephemeris;

import java.time.Instant;

/**
 * Geocentric sidereal positions of the nine KP grahas for a given instant.
 *
 * <p>Contract (see {@code specs/001-ephemeris-primitives/contracts/ephemeris-api.md}):
 *
 * <ul>
 *   <li>KP-New ayanamsa, mean node; Ketu is exactly opposite Rahu; both retrograde.</li>
 *   <li>Deterministic: equal input ⇒ {@code equals}-equal output, on every platform.</li>
 *   <li>Thread-safe.</li>
 *   <li>An instant outside the supported ephemeris range is not an error — the
 *       result carries {@link Accuracy#REDUCED} on every position.</li>
 *   <li>No Swiss Ephemeris type appears in this API (FR-018).</li>
 * </ul>
 */
public interface PositionProvider {

    /**
     * @param utcInstant a non-null instant on the UTC timeline
     * @throws EphemerisException if {@code utcInstant} is null or the backend cannot initialise
     */
    EphemerisResult positions(Instant utcInstant);
}
