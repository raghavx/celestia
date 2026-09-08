package com.celestia.ephemeris;

/**
 * Ties a set of engine outputs to the exact rules and data that produced them
 * (FR-015). Any change to a calculation rule, the ayanamsa mapping, the node
 * choice, the &Delta;T model, the Swiss Ephemeris port version, or the ephemeris
 * data set MUST change {@link #id()} so stored charts can be detected as stale.
 *
 * <p>Note the coupling documented in {@code core/REFERENCES.md}: a change to a
 * {@code core} algorithm (lord chain, Vimshottari partition) also requires bumping
 * {@link #rules()} here, even though {@code core} cannot reference this type.
 *
 * @param rules Celestia's own algorithm version, bumped by hand on any rule change
 * @param sePort the Swiss Ephemeris port version (e.g. {@code "2.01.00"})
 * @param deltaTModel a tag for the &Delta;T model in use
 * @param ephemerisData {@code "swieph"} + data identity, or {@code "moseph"}
 */
public record EngineVersion(String rules, String sePort, String deltaTModel, String ephemerisData) {

    /** Current algorithm version of the Celestia KP engine. */
    public static final String RULES = "kp-1";

    public String id() {
        return String.join("/", rules, sePort, deltaTModel, ephemerisData);
    }
}
