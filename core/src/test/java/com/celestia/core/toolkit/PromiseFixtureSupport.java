package com.celestia.core.toolkit;

import java.util.List;

/**
 * A small, hand-built rule book for the evaluator tests (SPEC-010 T022).
 *
 * <p>Hand-built rather than generated: each rule is chosen to pin one branch of
 * {@link PromiseHit.Status}, so the expected outcome is obvious from reading the
 * fixture. The synthetic chart in {@link PromiseEvaluatorTest} connects house 1
 * to exactly {7, 10, 11} (research.md &sect;3's prototype check), and the rules
 * below are written against that.
 */
final class PromiseFixtureSupport {

    static final String NAME = "promise-fixture";

    /** All houses connected, no condition — must be PROMISED. */
    static final String PROMISED_ID = "fixture:promised";
    /** All houses connected, condition present — must be CONDITION_TO_CHECK. */
    static final String CONDITION_ID = "fixture:condition";
    /** Some houses connected — PARTLY_INDICATED, and only when asked for. */
    static final String PARTIAL_ID = "fixture:partial";
    /** No houses at all — kept, retrievable, never auto-matched (FR-003). */
    static final String UNMATCHABLE_ID = "fixture:unmatchable";
    /** Health wording, to prove sensitivity reaches the house promise (FR-010a). */
    static final String HEALTH_ID = "fixture:health";
    /** Self-harm wording: classified HEALTH like any other rule (FR-011). */
    static final String SUICIDE_ID = "fixture:suicide";

    private PromiseFixtureSupport() {}

    /** Write the fixture root and return the classpath root to load it from. */
    static String write() {
        List<List<String>> rules = List.of(
                rule(PROMISED_ID, "gains through business", 1, "7,10,11", "", "NONE"),
                rule(CONDITION_ID, "gains through business", 1, "7,10,11", "+ Saturn", "NONE"),
                rule(PARTIAL_ID, "foreign travel for job", 1, "7,10,12", "", "NONE"),
                rule(UNMATCHABLE_ID, "Saturn as 1st sub lord", 1, "", "Saturn as 1st sub lord", "NONE"),
                rule(HEALTH_ID, "poor health and hospital stay", 1, "7,10", "", "HEALTH"),
                rule(SUICIDE_ID, "danger of suicide", 1, "7,11", "", "HEALTH"),
                // A rule on another house, so rulesFor(1) must not return it.
                rule("fixture:other-house", "an unrelated promise", 4, "4", "", "NONE"));

        // Every rule's source must resolve to a manifest sheet (validation at
        // load), so the fixture declares the one sheet its rules came from.
        ToolkitFixtures.writeRelease(NAME, 1, rules.size());
        ToolkitFixtures.writeTsv(
                NAME,
                "manifest.tsv",
                List.of("order", "id", "title", "group", "kind", "sensitivity", "answerable"),
                List.of(List.of("1", "fixture-rules", "Fixture Rules", "LIFE_MATTERS", "RULES", "NONE", "true")));
        ToolkitFixtures.writeTsv(
                NAME, "sheets/fixture-rules.tsv", List.of("cells"), List.of(List.of("fixture rule book")));
        ToolkitFixtures.writeTsv(
                NAME,
                "rules/promise-rules.tsv",
                List.of(
                        "id", "source", "category", "matter", "matterKey", "judgedHouse", "houses", "condition",
                        "sensitivity"),
                rules);
        return ToolkitFixtures.root(NAME);
    }

    private static List<String> rule(
            String id, String matter, int judgedHouse, String houses, String condition, String sensitivity) {
        return List.of(
                id,
                "fixture-rules",
                "Fixture",
                matter,
                matter.toLowerCase(java.util.Locale.ROOT),
                String.valueOf(judgedHouse),
                houses,
                condition,
                sensitivity);
    }
}
