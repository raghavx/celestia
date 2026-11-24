package com.celestia.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Intent versus a question about the chart (FR-015, SC-006, research.md §7).
 *
 * <p>This is the one place where two readings of the same subject matter diverge
 * completely: SPEC-010 FR-011 says a toolkit rule about self-harm is reported
 * like any other with the health advisory, while FR-011a says a user expressing
 * intent gets help first.
 */
class SelfHarmSignalTest {

    private static SelfHarmSignal of(String text) {
        return SelfHarmSignal.of(Normaliser.normalise(text).normalised());
    }

    @Test
    void firstPersonVolitionWithAHarmTermIsIntent() {
        assertThat(of("i want to die")).isEqualTo(SelfHarmSignal.INTENT);
        assertThat(of("i'm going to end it")).isEqualTo(SelfHarmSignal.INTENT);
        assertThat(of("i want to end it all tonight")).isEqualTo(SelfHarmSignal.INTENT);
        assertThat(of("i have decided to kill myself")).isEqualTo(SelfHarmSignal.INTENT);
        assertThat(of("i think i should just disappear forever")).isEqualTo(SelfHarmSignal.INTENT);
    }

    @Test
    void aQuestionAboutWhatTheChartIndicatesIsNotIntent() {
        // These are ordinary health-class content: the health advisory, no support
        // line (SPEC-010 FR-011 rather than FR-011a).
        assertThat(of("does my chart show suicide")).isEqualTo(SelfHarmSignal.CHART_INQUIRY);
        assertThat(of("is there danger to my life in my horoscope")).isEqualTo(SelfHarmSignal.CHART_INQUIRY);
        assertThat(of("what does the 8th house say about death")).isEqualTo(SelfHarmSignal.CHART_INQUIRY);
    }

    @Test
    void intentWinsWhenBothAppear() {
        // "my chart says I should die" carries a chart referent and an intent
        // construction. A support line shown to someone who only wanted a reading
        // costs little; a missed one is not recoverable.
        assertThat(of("my chart says i should die")).isEqualTo(SelfHarmSignal.INTENT);
    }

    @Test
    void harmWordsAloneMeanNothing() {
        // The Difficulty sheet is full of them. Keying on harm words rather than on
        // the construction would put a crisis line on every 8th-house question and
        // train the user to ignore it.
        assertThat(of("will my father's death be peaceful")).isEqualTo(SelfHarmSignal.NONE);
        assertThat(of("when will i get married")).isEqualTo(SelfHarmSignal.NONE);
        assertThat(of("")).isEqualTo(SelfHarmSignal.NONE);
    }

    @Test
    void theSignalIsTakenFromTheNormalisedViewSoHidingItDoesNotWork() {
        // Zero-width characters between letters must not hide the construction.
        assertThat(of("i wa\u200Bnt to d\u200Cie")).isEqualTo(SelfHarmSignal.INTENT);
    }
}
