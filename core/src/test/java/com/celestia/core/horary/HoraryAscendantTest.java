package com.celestia.core.horary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.core.chart.AnglePoint;
import com.celestia.ephemeris.Angle;
import com.celestia.ephemeris.Graha;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC-005 US2: the horary Ascendant is the arc midpoint, clock-independent. */
class HoraryAscendantTest {

    @Test
    void ascendantIsTheArcMidpointWithTheArcsLordChain() {
        for (int n = 1; n <= 249; n++) {
            HoraryArc arc = Horary249.arc(n);
            AnglePoint asc = HoraryChartFactory.ascendant(n);
            assertThat(asc.angle()).isEqualTo(Angle.ASCENDANT);
            assertThat(asc.longitude()).isEqualTo(arc.midpointDeg());
            assertThat(asc.lordChain().subLord()).isEqualTo(arc.subLord());
        }
    }

    @Test
    void numbersSharingASubLordSitInDifferentSigns() {
        // group numbers by sub lord; any sub lord with >1 arc came from a
        // sign-boundary split, and those arcs must be in different signs
        Map<Graha, List<Integer>> bySubLord = new HashMap<>();
        for (int n = 1; n <= 249; n++) {
            bySubLord.computeIfAbsent(Horary249.arc(n).subLord(), k -> new java.util.ArrayList<>()).add(n);
        }
        boolean sawASplit = false;
        for (var entry : bySubLord.entrySet()) {
            List<Integer> numbers = entry.getValue();
            for (int i = 0; i + 1 < numbers.size(); i++) {
                HoraryArc a = Horary249.arc(numbers.get(i));
                HoraryArc b = Horary249.arc(numbers.get(i + 1));
                if (a.end().equals(b.start())) { // adjacent -> a sign-boundary split
                    sawASplit = true;
                    assertThat(a.sign()).isNotEqualTo(b.sign());
                    assertThat(HoraryChartFactory.ascendant(a.number()).lordChain().subLord())
                            .isEqualTo(HoraryChartFactory.ascendant(b.number()).lordChain().subLord());
                }
            }
        }
        assertThat(sawASplit).as("the 249 table has sign-boundary splits").isTrue();
    }

    @Test
    void rejectsNumbersOutOfRange() {
        assertThatThrownBy(() -> HoraryChartFactory.ascendant(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HoraryChartFactory.ascendant(250))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
