package com.celestia.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.toolkit.Sensitivity;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SPEC-011 FR-014 — which class's advisory sentence a reply ends with.
 *
 * <p>FR-014 says the most severe class <b>used</b>, and the implementation read
 * that as the most severe class <i>returned</i>. T041 showed what the difference
 * costs: eight of ten answers on the owner's own chart ended in a health or
 * longevity caution, most of them about a house the question never touched —
 * asked what subject he was suited to study, he was told to see a doctor.
 *
 * <p>These pin the narrowing. Every one of them passed before the fix only
 * because nothing exercised a house-borne sensitivity at all.
 */
class AdvisoryNarrowingTest {

    private static TurnScope scope() {
        return new TurnScope(
                TurnFixtures.context("what subject was I best suited to study?"),
                Instant.parse("2026-11-24T12:00:00Z"), Duration.ofSeconds(60), 6);
    }

    @Test
    @DisplayName("a house the question never asked about cannot impose its advisory")
    void anUnaskedHouseDoesNotCount() {
        // The T041 defect exactly: getChartPromises with no house returns all
        // twelve, so the 8th house's longevity matters reached a question about
        // education and ended the reply with "consult a doctor".
        TurnScope scope = scope();
        scope.askedAboutHouse(4);
        scope.askedAboutHouse(9);
        scope.sawSensitivity(4, Sensitivity.NONE);
        scope.sawSensitivity(9, Sensitivity.NONE);
        scope.sawSensitivity(8, Sensitivity.LONGEVITY); // swept up, never asked for

        assertThat(scope.sensitivityUsed()).isEqualTo(Sensitivity.NONE);
    }

    @Test
    @DisplayName("the advisory still follows the house that WAS asked about")
    void anAskedHouseStillCounts() {
        TurnScope scope = scope();
        scope.askedAboutHouse(6);
        scope.sawSensitivity(6, Sensitivity.HEALTH);
        scope.sawSensitivity(2, Sensitivity.FINANCE);

        assertThat(scope.sensitivityUsed())
                .as("a health question must keep its health advisory — that is the point of FR-014")
                .isEqualTo(Sensitivity.HEALTH);
    }

    @Test
    @DisplayName("the most severe of the houses asked about wins, not the most severe seen")
    void theWorstOfTheAskedHousesWins() {
        TurnScope scope = scope();
        scope.askedAboutHouse(2);
        scope.askedAboutHouse(8);
        scope.sawSensitivity(2, Sensitivity.FINANCE);
        scope.sawSensitivity(8, Sensitivity.LONGEVITY);
        scope.sawSensitivity(11, Sensitivity.HEALTH); // not asked about

        assertThat(scope.sensitivityUsed()).isEqualTo(Sensitivity.LONGEVITY);
    }

    @Test
    @DisplayName("content with no house behind it always counts — searching for it is the evidence")
    void untargetedContentAlwaysCounts() {
        // A sheet the model searched for, or the disease table it read, is not
        // swept up incidentally: asking for it is itself the sign that the answer
        // is about it. Narrowing must not swallow these.
        TurnScope scope = scope();
        scope.askedAboutHouse(4);
        scope.sawSensitivity(4, Sensitivity.NONE);
        scope.sawSensitivity(Sensitivity.HEALTH);

        assertThat(scope.sensitivityUsed()).isEqualTo(Sensitivity.HEALTH);
    }

    @Test
    @DisplayName("with no house asked about, every house still counts — there is nothing to narrow by")
    void withoutATargetTheOldConservativeReadingHolds() {
        TurnScope scope = scope();
        scope.sawSensitivity(8, Sensitivity.LONGEVITY);

        assertThat(scope.sensitivityUsed())
                .as("narrowing needs a target; absent one, the safe reading is the right one")
                .isEqualTo(Sensitivity.LONGEVITY);
    }

    @Test
    @DisplayName("nothing sensitive at all stays NONE, so no advisory is appended")
    void nothingSensitiveStaysNone() {
        TurnScope scope = scope();
        scope.askedAboutHouse(9);
        scope.sawSensitivity(9, Sensitivity.NONE);

        assertThat(scope.sensitivityUsed()).isEqualTo(Sensitivity.NONE);
    }

    @Test
    @DisplayName("the houses asked about are recorded, and only by number")
    void theAskedHousesAreRecorded() {
        TurnScope scope = scope();
        scope.askedAboutHouse(7);
        scope.askedAboutHouse(7);
        scope.askedAboutHouse(11);

        assertThat(scope.housesAskedAbout()).containsExactlyInAnyOrder(7, 11);
    }
}
