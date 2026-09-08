package com.celestia.ephemeris;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * The full sidereal position set for one instant.
 *
 * @param instant the UTC instant this was computed for (echoed input)
 * @param julianDay the derived time scales
 * @param ayanamsa the sidereal reference used (always {@link Ayanamsa#KP_NEW})
 * @param positions exactly the nine {@link Graha}, immutable
 * @param engineVersion the rules + data identity that produced this
 */
public record EphemerisResult(
        Instant instant,
        JulianDay julianDay,
        Ayanamsa ayanamsa,
        Map<Graha, GrahaPosition> positions,
        EngineVersion engineVersion) {

    public EphemerisResult {
        if (!positions.keySet().equals(EnumSet.allOf(Graha.class))) {
            throw new IllegalArgumentException("positions must contain exactly the nine grahas, was " + positions.keySet());
        }
        positions = Collections.unmodifiableMap(new EnumMap<>(positions));
    }

    /** Convenience accessor for one graha's position. */
    public GrahaPosition position(Graha graha) {
        return positions.get(graha);
    }
}
