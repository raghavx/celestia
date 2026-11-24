package com.celestia.agent.llm;

import java.util.List;
import java.util.Set;

/**
 * Every tool the model may reach, named once (FR-007).
 *
 * <p>The model is given a <b>fixed</b> tool set — the engine tools plus the five
 * toolkit tools — and can call nothing else. {@code ToolSetClosedTest} asserts
 * that the registered callbacks equal this list exactly, so an accidentally-added
 * {@code @Tool} fails the build rather than quietly widening what the model can
 * reach.
 *
 * <p>The split matters for SC-002: only a {@link #TOOLKIT} call can ground a KP
 * interpretation. {@link #ENGINE} results carry no toolkit source — they are
 * numbers, and a number is not a judgement.
 */
public final class ToolNames {

    /** The deterministic engine. Every number the answer states comes from one of these (FR-008). */
    public static final Set<String> ENGINE = Set.of(
            "getNatalChart",
            "getRunningDasha",
            "getSignificators",
            "getRulingPlanets",
            "castHorary",
            "castChartFor");

    /** SPEC-010's five. The only tools that can ground an interpretation (Constitution X). */
    public static final Set<String> TOOLKIT = Set.of(
            "getChartPromises",
            "findToolkitRules",
            "lookupToolkit",
            "getToolkitLookup",
            "getHoraryMindset");

    /** Engine plus toolkit, sorted — the closed set the build checks against. */
    public static List<String> all() {
        return java.util.stream.Stream.concat(ENGINE.stream(), TOOLKIT.stream())
                .sorted()
                .toList();
    }

    private ToolNames() {}
}
