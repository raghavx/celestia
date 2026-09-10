package com.celestia.agent.persistence;

import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.PositionProvider;
import com.celestia.ephemeris.TimeScales;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/**
 * The live {@link EngineVersion} the cache stamps on a fresh result and compares
 * a stored row against (SPEC-008 FR-006).
 *
 * <ul>
 *   <li>{@code rules} = {@link EngineVersion#RULES} (bumped by hand on a KP rule
 *       change, per {@code core/REFERENCES.md}).</li>
 *   <li>{@code deltaTModel} = {@link TimeScales#DELTA_T_MODEL}.</li>
 *   <li>{@code sePort} = the Swiss Ephemeris port version, learned once from a
 *       probe computation at startup.</li>
 *   <li>{@code ephemerisData} = {@code "swieph"} when the birth year is inside
 *       the configured {@code .se1} range, else {@code "moseph"} — predicted from
 *       the year, not from a computation. On the rare mismatch (a per-planet
 *       Moshier fallback inside the range) the cache simply recomputes.</li>
 * </ul>
 */
@Component
public class CurrentEngineVersion {

    private final SwissEphemerisConfig config;
    private final String sePort;

    public CurrentEngineVersion(PositionProvider positionProvider, SwissEphemerisConfig config) {
        this.config = config;
        // One probe at startup to learn the SE port version string.
        this.sePort = positionProvider
                .positions(Instant.parse("2000-01-01T00:00:00Z"))
                .engineVersion()
                .sePort();
    }

    public EngineVersion forBirth(BirthData birth) {
        int year = birth.instant().atOffset(ZoneOffset.UTC).getYear();
        String ephemerisData = config.supports(year) ? "swieph" : "moseph";
        return new EngineVersion(EngineVersion.RULES, sePort, TimeScales.DELTA_T_MODEL, ephemerisData);
    }

    public String id(BirthData birth) {
        return forBirth(birth).id();
    }
}
