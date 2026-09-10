package com.celestia.core.horary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.core.lordage.Sign;
import com.celestia.ephemeris.Graha;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/** SPEC-005 US1: number → arc lookup. */
class Horary249Test {

    @Test
    void rejectsNumbersOutsideOneToTwoFortyNine() {
        assertThatThrownBy(() -> Horary249.arc(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Horary249.arc(250)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Horary249.arc(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theTableStartsAtZeroAndEndsAtThreeSixty() {
        assertThat(Horary249.arc(1).startDeg()).isEqualTo(0.0);
        assertThat(Horary249.arc(249).endDeg()).isCloseTo(360.0, Offset.offset(1e-9));
    }

    @Test
    void arcOneHundredMatchesTheGoldenReference() {
        // obama-1961 golden expected.horary: number 100 -> sub lord Mercury, Leo
        HoraryArc a = Horary249.arc(100);
        assertThat(a.subLord()).isEqualTo(Graha.MERCURY);
        assertThat(a.sign()).isEqualTo(Sign.LEO);
        assertThat(a.midpointDeg()).isCloseTo(144.944444, Offset.offset(1e-4));
    }

    @Test
    void everyNumberResolvesToAnArcCarryingThatNumber() {
        for (int n = 1; n <= 249; n++) {
            assertThat(Horary249.arc(n).number()).isEqualTo(n);
        }
    }
}
