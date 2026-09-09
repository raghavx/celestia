package com.celestia.core.horary;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.dasha.Span;
import com.celestia.core.dasha.VimshottariPartition;
import com.celestia.core.lordage.Sign;
import java.util.List;
import org.apache.commons.numbers.fraction.BigFraction;
import org.junit.jupiter.api.Test;

/** SPEC-005 SC-001: the 249 arcs tile [0°, 360°) exactly, and there are exactly 249. */
class Horary249TilingPropertyTest {

    @Test
    void thereAreExactly249Arcs() {
        assertThat(Horary249.count()).isEqualTo(249);
        assertThat(Horary249.arcs()).hasSize(249);
    }

    @Test
    void theArcsTileTheZodiacExactly() {
        List<HoraryArc> arcs = Horary249.arcs();
        assertThat(arcs.get(0).start()).isEqualTo(BigFraction.ZERO);
        assertThat(arcs.get(248).end()).isEqualTo(BigFraction.of(360));
        for (int i = 0; i < arcs.size() - 1; i++) {
            assertThat(arcs.get(i).end())
                    .as("arc %d end == arc %d start", i + 1, i + 2)
                    .isEqualTo(arcs.get(i + 1).start());
        }
    }

    @Test
    void everyArcLiesInOneSignAndIsNumberedInOrder() {
        List<HoraryArc> arcs = Horary249.arcs();
        for (int i = 0; i < arcs.size(); i++) {
            HoraryArc a = arcs.get(i);
            assertThat(a.number()).isEqualTo(i + 1);
            assertThat(a.start().compareTo(a.end())).isNegative();
            assertThat(Sign.at(a.startDeg())).isEqualTo(a.sign());
            // end is exclusive; a point just inside the arc is still in the sign
            assertThat(Sign.at(Math.nextDown(a.endDeg()))).isEqualTo(a.sign());
        }
    }

    @Test
    void everyArcSubLordMatchesTheCoveringSubDivisionSpan() {
        for (HoraryArc arc : Horary249.arcs()) {
            double mid = arc.midpointDeg();
            Span covering = VimshottariPartition.subDivisions().stream()
                    .filter(s -> s.contains(mid))
                    .findFirst()
                    .orElseThrow();
            assertThat(arc.subLord())
                    .as("arc %d sub lord vs its subDivisions() span", arc.number())
                    .isEqualTo(covering.lord());
        }
    }
}
