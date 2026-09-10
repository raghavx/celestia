package com.celestia.geo.time;

/**
 * Latitude/longitude to IANA time zone — offline and deterministic.
 *
 * <p>Contract (see {@code specs/007-geocoding-timezone/contracts/geo-api.md}):
 *
 * <ul>
 *   <li>A point inside a timezone polygon resolves to that zone.</li>
 *   <li>A point with no polygon resolves to a documented {@code Etc/GMT}
 *       longitude fallback with {@code approximated == true} (research.md §2).</li>
 *   <li>Deterministic and thread-safe; no wall clock; no implementation type in
 *       this API (the {@code timeshape} type stays behind it — ArchUnit-enforced).</li>
 * </ul>
 */
public interface TimeZoneResolver {

    /**
     * @param latitude {@code |latitude| <= 90}
     * @param longitude {@code |longitude| <= 180}
     */
    ZoneResolution resolve(double latitude, double longitude);
}
