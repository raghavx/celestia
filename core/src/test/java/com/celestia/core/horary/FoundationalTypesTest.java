package com.celestia.core.horary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.core.lordage.KpLordage;
import com.celestia.core.lordage.Sign;
import org.apache.commons.numbers.fraction.BigFraction;
import org.junit.jupiter.api.Test;

class FoundationalTypesTest {

    private static HoraryArc arc(int number, double startDeg, double endDeg) {
        double mid = (startDeg + endDeg) / 2.0;
        return new HoraryArc(number, BigFraction.from(startDeg), BigFraction.from(endDeg),
                Sign.at(mid), KpLordage.chainFor(mid));
    }

    @Test
    void rejectsNumberOutOfRange() {
        assertThatThrownBy(() -> arc(0, 0.0, 1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> arc(250, 0.0, 1.0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveSpan() {
        assertThatThrownBy(() -> arc(1, 10.0, 10.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> arc(1, 10.0, 5.0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAMismatchedSign() {
        assertThatThrownBy(() -> new HoraryArc(
                1, BigFraction.from(1.0), BigFraction.from(2.0), Sign.TAURUS,
                KpLordage.chainFor(1.5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void containsIsHalfOpenAndSubLordMatchesTheChain() {
        HoraryArc a = arc(5, 40.0, 43.0); // Taurus
        assertThat(a.contains(40.0)).isTrue();
        assertThat(a.contains(42.999)).isTrue();
        assertThat(a.contains(43.0)).isFalse();
        assertThat(a.contains(39.999)).isFalse();
        assertThat(a.subLord()).isEqualTo(a.lordChain().subLord());
        assertThat(a.sign()).isEqualTo(Sign.TAURUS);
        assertThat(a.midpointDeg()).isEqualTo(41.5);
    }
}
