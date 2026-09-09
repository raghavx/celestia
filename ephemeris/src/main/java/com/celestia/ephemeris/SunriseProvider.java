package com.celestia.ephemeris;

import java.time.Instant;
import java.util.Optional;

/**
 * Local sunrise, for the KP day-lord boundary.
 *
 * <p>Contract (see
 * {@code specs/003-significators-ruling-planets/contracts/sunrise-provider-api.md}):
 *
 * <ul>
 *   <li>{@link #sunriseBefore} returns the latest local sunrise at or before the
 *       given instant — the sunrise that began the KP day containing it — or
 *       {@code Optional.empty()} on a polar day / night.</li>
 *   <li>Sunrise = the Sun's upper limb at the true horizon with standard refraction.</li>
 *   <li>Deterministic, thread-safe; no Swiss Ephemeris type in this API.</li>
 * </ul>
 */
public interface SunriseProvider {

    /**
     * @param judgmentInstant non-null; UTC timeline
     * @param latitude {@code |latitude| <= 90} — else {@code IllegalArgumentException}
     * @param longitude {@code |longitude| <= 180} — else {@code IllegalArgumentException}
     */
    Optional<Instant> sunriseBefore(Instant judgmentInstant, double latitude, double longitude);
}
