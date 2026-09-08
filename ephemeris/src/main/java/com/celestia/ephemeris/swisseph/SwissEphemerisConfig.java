package com.celestia.ephemeris.swisseph;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Where the {@code .se1} data lives and what the supported full-accuracy range is
 * (ADR-0002).
 *
 * <p>The ephemeris path is a <b>filesystem directory</b> ({@code swe_set_ephe_path}
 * cannot read a jar). Resolution order:
 *
 * <ol>
 *   <li>system property {@code celestia.ephe.path}</li>
 *   <li>env var {@code CELESTIA_EPHE_PATH}</li>
 *   <li>{@code ephemeris/src/main/resources/ephe} relative to the working dir (dev / tests)</li>
 *   <li>none → {@code null}, and the provider uses the Moshier model (REDUCED accuracy)</li>
 * </ol>
 *
 * @param ephePath directory containing {@code sepl_*.se1} / {@code semo_*.se1}, or {@code null}
 * @param minYear first year of full accuracy (inclusive)
 * @param maxYear last year of full accuracy (inclusive)
 */
public record SwissEphemerisConfig(String ephePath, int minYear, int maxYear) {

    private static final int DEFAULT_MIN_YEAR = 1800;
    private static final int DEFAULT_MAX_YEAR = 2100;

    /** Resolve from system property / env / conventional path. */
    public static SwissEphemerisConfig resolve() {
        return new SwissEphemerisConfig(resolveEphePath(), DEFAULT_MIN_YEAR, DEFAULT_MAX_YEAR);
    }

    public boolean hasEphemerisData() {
        return ephePath != null;
    }

    /** True if {@code year} is inside the supported full-accuracy range. */
    public boolean supports(int year) {
        return hasEphemerisData() && year >= minYear && year <= maxYear;
    }

    private static String resolveEphePath() {
        String fromProp = System.getProperty("celestia.ephe.path");
        if (isReadableDir(fromProp)) {
            return fromProp;
        }
        String fromEnv = System.getenv("CELESTIA_EPHE_PATH");
        if (isReadableDir(fromEnv)) {
            return fromEnv;
        }
        // conventional source-tree locations, whether run from the repo root, the
        // ephemeris module dir, or a sibling module dir (e.g. core tests)
        for (String conventional : new String[] {
            "ephemeris/src/main/resources/ephe",
            "src/main/resources/ephe",
            "../ephemeris/src/main/resources/ephe"
        }) {
            if (isReadableDir(conventional)) {
                return Path.of(conventional).toAbsolutePath().normalize().toString();
            }
        }
        return null;
    }

    private static boolean isReadableDir(String p) {
        return p != null && !p.isBlank() && Files.isDirectory(Path.of(p));
    }
}
