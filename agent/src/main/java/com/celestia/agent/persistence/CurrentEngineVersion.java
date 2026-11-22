package com.celestia.agent.persistence;

import com.celestia.core.toolkit.KpToolkit;
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
 *       change, per {@code core/REFERENCES.md}) joined with the bundled toolkit's
 *       release id, giving e.g. {@code kp-1+toolkit-2026-09-12}. {@code ephemeris}
 *       cannot depend on {@code core}, so the two are composed here (SPEC-010
 *       research.md &sect;7). A toolkit release therefore invalidates every cached
 *       chart and reading, which is what keeps a stored answer explainable by the
 *       toolkit that produced it (SPEC-010 FR-009, Constitution III).</li>
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
    private final String rules;

    public CurrentEngineVersion(PositionProvider positionProvider, SwissEphemerisConfig config) {
        this.config = config;
        this.rules = composeRules();
        // One probe at startup to learn the SE port version string.
        this.sePort = positionProvider
                .positions(Instant.parse("2000-01-01T00:00:00Z"))
                .engineVersion()
                .sePort();
    }

    /**
     * {@code kp-N+toolkit-<release>}, or plain {@code kp-N} before the toolkit
     * has been imported.
     *
     * <p>The toolkit's resources are generated from the licensed workbook, which
     * is never committed, so on a fresh clone they are absent until the owner
     * runs {@code tools/toolkit-import/import_toolkit.py} (SPEC-010 T047).
     * Falling back keeps the flows that need no toolkit — onboarding, the
     * WhatsApp worker, persistence — starting and testable meanwhile.
     *
     * <p>This is safe for the cache rather than merely convenient: importing the
     * toolkit later <em>changes</em> this string, so every chart and reading
     * stamped before the import is recomputed exactly as a toolkit upgrade would
     * force (SPEC-010 FR-009).
     */
    private static String composeRules() {
        try {
            return EngineVersion.RULES + "+" + KpToolkit.bundled().version().id();
        } catch (IllegalStateException toolkitNotImported) {
            return EngineVersion.RULES;
        }
    }

    public EngineVersion forBirth(BirthData birth) {
        int year = birth.instant().atOffset(ZoneOffset.UTC).getYear();
        String ephemerisData = config.supports(year) ? "swieph" : "moseph";
        return new EngineVersion(rules, sePort, TimeScales.DELTA_T_MODEL, ephemerisData);
    }

    public String id(BirthData birth) {
        return forBirth(birth).id();
    }
}
