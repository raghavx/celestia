package com.celestia.core.toolkit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * T023 — the advisory sentences and their ordering (SPEC-010 FR-010a).
 *
 * <p>The exact wording is product copy, set in {@link Sensitivity} and nowhere
 * else (Constitution V: never generated per reply). These assertions are
 * deliberately literal: a reworded advisory should fail the build and be a
 * conscious decision, not drift.
 */
class SensitivityTest {

    @Test
    void noneCarriesNoAdvisory() {
        assertThat(Sensitivity.NONE.note()).isEmpty();
    }

    @Test
    void theFourAdvisorySentencesAreTheProductsOwnWording() {
        assertThat(Sensitivity.HEALTH.note())
                .isEqualTo(
                        "Astrological guidance only — please consult a qualified doctor for diagnosis or treatment.");
        assertThat(Sensitivity.LONGEVITY.note())
                .isEqualTo(
                        "Astrological indications only — not a prediction of any certain event; please consult a doctor about any health concern.");
        assertThat(Sensitivity.FINANCE.note())
                .isEqualTo(
                        "Astrological guidance only — not financial advice; consult a registered financial advisor before investing.");
        assertThat(Sensitivity.REMEDY.note())
                .isEqualTo("Traditional remedy — complementary to, never a replacement for, professional advice.");
    }

    @Test
    void everyClassButNoneHasExactlyOneSentence() {
        for (Sensitivity sensitivity : Sensitivity.values()) {
            if (sensitivity == Sensitivity.NONE) {
                continue;
            }
            assertThat(sensitivity.note()).isNotBlank().endsWith(".");
            assertThat(sensitivity.note().split("(?<=\\.)\\s+")).hasSize(1);
        }
    }

    @Test
    void severityOrdersNoneBelowRemedyBelowFinanceBelowHealthBelowLongevity() {
        assertThat(Sensitivity.values())
                .containsExactly(
                        Sensitivity.NONE,
                        Sensitivity.REMEDY,
                        Sensitivity.FINANCE,
                        Sensitivity.HEALTH,
                        Sensitivity.LONGEVITY);
    }

    @Test
    void maxReturnsTheMoreSevereClassEitherWayRound() {
        assertThat(Sensitivity.NONE.max(Sensitivity.HEALTH)).isEqualTo(Sensitivity.HEALTH);
        assertThat(Sensitivity.HEALTH.max(Sensitivity.NONE)).isEqualTo(Sensitivity.HEALTH);
        assertThat(Sensitivity.FINANCE.max(Sensitivity.LONGEVITY)).isEqualTo(Sensitivity.LONGEVITY);
        assertThat(Sensitivity.REMEDY.max(Sensitivity.REMEDY)).isEqualTo(Sensitivity.REMEDY);
    }

    @Test
    void aSheetsDefaultSensitivitySurvivesTheImport() {
        KpToolkit toolkit = KpToolkit.load(SheetFixtureSupport.write());
        assertThat(toolkit.sheet("fixture-medical").orElseThrow().sensitivity())
                .isEqualTo(Sensitivity.HEALTH);
        assertThat(toolkit.sheet("fixture-longevity").orElseThrow().sensitivity())
                .isEqualTo(Sensitivity.LONGEVITY);
        assertThat(toolkit.sheet("fixture-finance").orElseThrow().sensitivity())
                .isEqualTo(Sensitivity.FINANCE);
        assertThat(toolkit.sheet("fixture-vastu").orElseThrow().sensitivity())
                .isEqualTo(Sensitivity.REMEDY);
    }
}
