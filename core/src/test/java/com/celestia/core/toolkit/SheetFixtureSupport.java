package com.celestia.core.toolkit;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the general sheet fixture (SPEC-010 T012) and the deliberately broken
 * one that proves load validation bites (T013).
 *
 * <p>The sheet set is chosen to exercise the checks that do not need licensed
 * text: one sheet per {@link SheetKind}; a non-answerable housekeeping sheet, so
 * the FR-014 exclusion can be asserted; and one answerable sheet in each of the
 * five topic groups FR-010 names explicitly (health, longevity, finance, Vastu,
 * numerology), so "the sensitive and beyond-natal-KP groups are answerable" is
 * a real assertion rather than a vacuous one.
 */
final class SheetFixtureSupport {

    static final String NAME = "fixture";
    static final String BROKEN_NAME = "broken-fixture";

    /** A sheet row the search test looks for by term. */
    static final String PROCEDURE_STEP = "Step 1 note the dasha lord and the bhukti lord";

    private record Spec(
            String id, String title, TopicGroup group, SheetKind kind, Sensitivity sensitivity, boolean answerable) {}

    private static final List<Spec> SHEETS = List.of(
            new Spec("fixture-rules", "Fixture Rules", TopicGroup.LIFE_MATTERS, SheetKind.RULES, Sensitivity.NONE, true),
            new Spec(
                    "fixture-table",
                    "Fixture Table",
                    TopicGroup.KP_FOUNDATIONS,
                    SheetKind.TABLE,
                    Sensitivity.NONE,
                    true),
            new Spec(
                    "time-of-event",
                    "Time of Event",
                    TopicGroup.PREDICTION_METHOD,
                    SheetKind.PROCEDURE,
                    Sensitivity.NONE,
                    true),
            new Spec(
                    "fixture-template",
                    "Fixture Template",
                    TopicGroup.PREDICTION_METHOD,
                    SheetKind.TEMPLATE,
                    Sensitivity.NONE,
                    true),
            // The five groups FR-010 names as answerable, one answerable sheet each.
            new Spec(
                    "fixture-medical",
                    "Fixture Medical",
                    TopicGroup.HEALTH_MEDICAL,
                    SheetKind.RULES,
                    Sensitivity.HEALTH,
                    true),
            new Spec(
                    "fixture-longevity",
                    "Fixture Longevity",
                    TopicGroup.LONGEVITY,
                    SheetKind.RULES,
                    Sensitivity.LONGEVITY,
                    true),
            new Spec(
                    "fixture-finance",
                    "Fixture Finance",
                    TopicGroup.FINANCE_SPECULATION,
                    SheetKind.RULES,
                    Sensitivity.FINANCE,
                    true),
            new Spec(
                    "fixture-vastu",
                    "Fixture Vastu",
                    TopicGroup.VASTU_FENG_SHUI,
                    SheetKind.REMEDY,
                    Sensitivity.REMEDY,
                    true),
            new Spec(
                    "fixture-numerology",
                    "Fixture Numerology",
                    TopicGroup.NUMEROLOGY,
                    SheetKind.PROCEDURE,
                    Sensitivity.NONE,
                    true),
            // Never answer content (FR-014).
            new Spec(
                    "fixture-index",
                    "Fixture INDEX",
                    TopicGroup.HOUSEKEEPING,
                    SheetKind.HOUSEKEEPING,
                    Sensitivity.NONE,
                    false));

    /** The groups FR-010 names, which must all be answerable. */
    static final List<TopicGroup> ANSWERABLE_GROUPS = List.of(
            TopicGroup.HEALTH_MEDICAL,
            TopicGroup.LONGEVITY,
            TopicGroup.FINANCE_SPECULATION,
            TopicGroup.VASTU_FENG_SHUI,
            TopicGroup.NUMEROLOGY);

    private SheetFixtureSupport() {}

    /** Write the fixture root and return the classpath root to load it from. */
    static String write() {
        List<List<String>> manifest = new ArrayList<>();
        int order = 1;
        for (Spec spec : SHEETS) {
            manifest.add(List.of(
                    String.valueOf(order++),
                    spec.id(),
                    spec.title(),
                    spec.group().name(),
                    spec.kind().name(),
                    spec.sensitivity().name(),
                    String.valueOf(spec.answerable())));
            ToolkitFixtures.writeTsv(NAME, "sheets/" + spec.id() + ".tsv", List.of("cells"), rowsFor(spec));
        }
        ToolkitFixtures.writeRelease(NAME, SHEETS.size(), 1);
        ToolkitFixtures.writeTsv(
                NAME,
                "manifest.tsv",
                List.of("order", "id", "title", "group", "kind", "sensitivity", "answerable"),
                manifest);

        // One rule, so the rule count in toolkit.properties is non-trivial and
        // the "every rule's source is a manifest id" check has something to chew.
        ToolkitFixtures.writeTsv(
                NAME,
                "rules/promise-rules.tsv",
                List.of(
                        "id", "source", "category", "matter", "matterKey", "judgedHouse", "houses", "condition",
                        "sensitivity"),
                List.of(List.of(
                        "fixture-rules:r1",
                        "fixture-rules",
                        "Fixture Rules",
                        "a settled fixture promise",
                        "a settled fixture promise",
                        "7",
                        "2,7,11",
                        "",
                        "NONE")));
        return ToolkitFixtures.root(NAME);
    }

    private static List<List<String>> rowsFor(Spec spec) {
        if (spec.kind() == SheetKind.PROCEDURE && spec.id().equals("time-of-event")) {
            // Ordered steps, so FR-007's "retrieved in written order" is testable.
            return List.of(
                    List.of(PROCEDURE_STEP),
                    List.of("Step 2 note the current ruling planets"),
                    List.of("Step 3 take the common significators"));
        }
        return List.of(List.of(spec.title() + " first row"), List.of(spec.title() + " second row"));
    }

    /**
     * A root whose {@code toolkit.properties} and {@code manifest.tsv} disagree,
     * so load-time validation has something to reject (T013).
     */
    static String writeBroken() {
        ToolkitFixtures.writeRelease(BROKEN_NAME, 99, 0);
        ToolkitFixtures.writeTsv(
                BROKEN_NAME,
                "manifest.tsv",
                List.of("order", "id", "title", "group", "kind", "sensitivity", "answerable"),
                List.of());
        return ToolkitFixtures.root(BROKEN_NAME);
    }
}
