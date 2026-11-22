package com.celestia.core.toolkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * T037 — procedure sheets come back verbatim and in order (SPEC-010 FR-007).
 *
 * <p>Order is the whole point of a procedure: "note the dasha lord, then the
 * bhukti lord, then the ruling planets" is a different method if shuffled. The
 * toolkit's steps are what stop the agent improvising method, which the
 * constitution forbids for numbers and which is risky for judgement
 * (Constitution X).
 *
 * <p>Runs against the fixture's own procedure sheet, so the behaviour under test
 * — ordered, verbatim retrieval, findable by topic — holds on any checkout. The
 * real sheets (BTR, Annual Prediction, Marriage Matching, …) are covered by
 * {@link ToolkitCompletenessTest}'s real-toolkit group.
 */
class ToolkitProcedureTest {

    private static KpToolkit toolkit;

    @BeforeAll
    static void loadFixture() {
        toolkit = KpToolkit.load(SheetFixtureSupport.write());
    }

    @Test
    void aProcedureSheetKeepsItsStepsInWrittenOrder() {
        ToolkitSheet procedure = toolkit.sheet("time-of-event").orElseThrow();
        assertThat(procedure.kind()).isEqualTo(SheetKind.PROCEDURE);
        assertThat(procedure.rows().stream().map(row -> row.get(0)).toList())
                .containsExactly(
                        SheetFixtureSupport.PROCEDURE_STEP,
                        "Step 2 note the current ruling planets",
                        "Step 3 take the common significators");
    }

    @Test
    void everyProcedureAndTemplateSheetIsRetrievableAndNonEmpty() {
        List<ToolkitSheet> procedures = toolkit.sheets().stream()
                .filter(s -> s.kind() == SheetKind.PROCEDURE || s.kind() == SheetKind.TEMPLATE)
                .toList();
        assertThat(procedures).isNotEmpty();
        for (ToolkitSheet sheet : procedures) {
            assertThat(toolkit.sheet(sheet.id())).isPresent();
            assertThat(sheet.rowCount()).as("%s has steps", sheet.id()).isPositive();
        }
    }

    @Test
    void searchingByTopicReturnsTheProcedureFirst() {
        // US4 acceptance scenario 1: "when will my health improve?" has to reach
        // the Time of Event steps.
        List<KpToolkit.SheetHit> hits = toolkit.search("time of event", 5);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).sheet().id()).isEqualTo("time-of-event");
    }

    @Test
    void aRowMatchIsReportedWithTheRowsThatMatched() {
        // The agent is handed matchingRows, so a term found in the body has to
        // come back with the rows it was found in - not just the sheet.
        List<KpToolkit.SheetHit> hits = toolkit.search("dasha", 5);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).sheet().id()).isEqualTo("time-of-event");
        assertThat(hits.get(0).matchingRows()).isNotEmpty();
        assertThat(hits.get(0).matchingRows().get(0).get(0)).contains("dasha lord");
    }

    @Test
    void aTitleMatchOutranksARowMatch() {
        List<KpToolkit.SheetHit> hits = toolkit.search("fixture", 10);
        assertThat(hits).isNotEmpty();
        assertThat(hits).isSortedAccordingTo(
                java.util.Comparator.comparingInt(KpToolkit.SheetHit::score).reversed());
    }

    @Test
    void searchIgnoresTermsTooShortToBeMeaningful() {
        assertThat(toolkit.search("of", 5)).isEmpty();
        assertThat(toolkit.search("a an", 5)).isEmpty();
    }

    @Test
    void anEmptyOrAbsentQueryReturnsNothingRatherThanEverything() {
        assertThat(toolkit.search("", 5)).isEmpty();
        assertThat(toolkit.search("   ", 5)).isEmpty();
        assertThat(toolkit.search(null, 5)).isEmpty();
        assertThat(toolkit.search("time of event", 0)).isEmpty();
    }

    @Test
    void searchRespectsTheLimit() {
        assertThat(toolkit.search("fixture", 2)).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void everyTermMustMatchForASheetToBeReturned() {
        assertThat(toolkit.search("time of event", 5)).isNotEmpty();
        assertThat(toolkit.search("time of event numerology", 5)).isEmpty();
    }
}
