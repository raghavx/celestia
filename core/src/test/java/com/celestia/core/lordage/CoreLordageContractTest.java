package com.celestia.core.lordage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Encodes contracts/core-lordage-api.md. */
class CoreLordageContractTest {

    @Test
    void isATotalFunctionAfterNormalisation() {
        for (double d = -720.0; d < 1080.0; d += 0.5) {
            LordChain c = KpLordage.chainFor(d);
            assertThat(c.longitude()).isGreaterThanOrEqualTo(0.0).isLessThan(360.0);
            assertThat(c.pada()).isBetween(1, 4);
            assertThat(c.signLord()).isEqualTo(c.sign().lord());
            assertThat(c.starLord()).isEqualTo(c.nakshatra().lord());
        }
    }

    @Test
    void rejectsNonFiniteInput() {
        assertThatThrownBy(() -> KpLordage.chainFor(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> KpLordage.chainFor(Double.POSITIVE_INFINITY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalisesBeforeDecomposing() {
        assertThat(KpLordage.chainFor(360.0).nakshatra()).isEqualTo(Nakshatra.ASHWINI);
        assertThat(KpLordage.chainFor(-360.0).nakshatra()).isEqualTo(Nakshatra.ASHWINI);
        assertThat(KpLordage.chainFor(365.0).longitude()).isEqualTo(5.0);
    }
}
