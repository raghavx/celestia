package com.celestia.core.lordage;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.dasha.Span;
import com.celestia.core.dasha.VimshottariPartition;
import java.util.List;
import org.junit.jupiter.api.Test;

class KpLordageBoundaryAndPadaTest {

    @Test
    void longitudeExactlyOnASubBoundaryBelongsToTheHigherSub() {
        // Ashwini's first sub (Ketu) ends at 7/9 deg; the second (Venus) starts there.
        List<Span> subs = VimshottariPartition.subs(Nakshatra.ASHWINI);
        double boundary = subs.get(0).endDeg(); // == subs.get(1).startDeg()
        assertThat(KpLordage.chainFor(boundary).subLord())
                .isEqualTo(subs.get(1).lord());
    }

    @Test
    void exactSignBoundariesGoToTheHigherSign() {
        assertThat(KpLordage.chainFor(30.0).sign()).isEqualTo(Sign.TAURUS);
        assertThat(KpLordage.chainFor(0.0).sign()).isEqualTo(Sign.ARIES);
        assertThat(KpLordage.chainFor(330.0).sign()).isEqualTo(Sign.PISCES);
    }

    @Test
    void eachNakshatraStartBeginsPadaOneAndEachPadaIsExactlyThreeTwenty() {
        for (Nakshatra n : Nakshatra.values()) {
            double start = n.startLongitude();
            assertThat(Nakshatra.padaAt(start + 1e-6)).as("pada 1 of %s", n).isEqualTo(1);
            assertThat(Nakshatra.padaAt(start + Nakshatra.PADA_DEGREES + 1e-6)).as("pada 2 of %s", n).isEqualTo(2);
            assertThat(Nakshatra.padaAt(start + 2 * Nakshatra.PADA_DEGREES + 1e-6)).as("pada 3 of %s", n).isEqualTo(3);
            assertThat(Nakshatra.padaAt(start + 3 * Nakshatra.PADA_DEGREES + 1e-6)).as("pada 4 of %s", n).isEqualTo(4);
        }
    }
}
