package com.celestia.core.judgement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.lordage.KpLordage;
import com.celestia.ephemeris.Graha;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC-003 US3: Rahu / Ketu signify by agency (FR-007, FR-008). */
class NodeAgencyTest {

    /** Equal cusps from Aries 0: a graha at L is in bhava floor(L/30)+1. */
    private static NatalChart chart(Map<Graha, Double> lons) {
        return SyntheticChart.of(lons, SyntheticChart.equalCusps(0.0));
    }

    @Test
    void nodeConjoinedWithAGrahaCountsThatGrahaAsAnAgent() {
        Map<Graha, Double> lons = new EnumMap<>(Graha.class);
        lons.put(Graha.SUN, 5.0);
        lons.put(Graha.MOON, 35.0);
        lons.put(Graha.MARS, 65.0);
        lons.put(Graha.MERCURY, 95.0);
        lons.put(Graha.JUPITER, 125.0);
        lons.put(Graha.VENUS, 155.0);
        lons.put(Graha.SATURN, 212.0); // house 8, with Rahu
        lons.put(Graha.RAHU, 215.0); // house 8
        lons.put(Graha.KETU, 245.0); // house 9, alone

        SignificatorTable table = SignificatorTable.of(chart(lons));
        NodeAgency rahu = table.nodeAgency(Graha.RAHU);
        assertThat(rahu.conjunctGrahas()).containsExactly(Graha.SATURN);
        assertThat(rahu.agents()).contains(Graha.SATURN, rahu.signLord(), rahu.starLord());
    }

    @Test
    void nodeAloneHasExactlyItsSignAndStarLordAsAgents() {
        Map<Graha, Double> lons = new EnumMap<>(Graha.class);
        lons.put(Graha.SUN, 5.0);
        lons.put(Graha.MOON, 35.0);
        lons.put(Graha.MARS, 65.0);
        lons.put(Graha.MERCURY, 95.0);
        lons.put(Graha.JUPITER, 125.0);
        lons.put(Graha.VENUS, 155.0);
        lons.put(Graha.SATURN, 185.0);
        lons.put(Graha.RAHU, 215.0); // house 8, alone
        lons.put(Graha.KETU, 245.0); // house 9, alone

        SignificatorTable table = SignificatorTable.of(chart(lons));
        NodeAgency rahu = table.nodeAgency(Graha.RAHU);

        assertThat(rahu.conjunctGrahas()).isEmpty();
        var expectedChain = KpLordage.chainFor(215.0);
        assertThat(rahu.signLord()).isEqualTo(expectedChain.signLord());
        assertThat(rahu.starLord()).isEqualTo(expectedChain.starLord());
        assertThat(rahu.agents())
                .containsExactlyInAnyOrder(expectedChain.signLord(), expectedChain.starLord());
    }

    @Test
    void agentsAreStepTwoOccupantsOfTheNodesOwnBhava() {
        Map<Graha, Double> lons = new EnumMap<>(Graha.class);
        double d = 7.0;
        for (Graha g : Graha.values()) {
            lons.put(g, d);
            d += 41.0;
        }
        NatalChart chart = chart(lons);
        SignificatorTable table = SignificatorTable.of(chart);

        for (Graha node : new Graha[] {Graha.RAHU, Graha.KETU}) {
            int bhava = chart.placement(node).bhava();
            HouseSignificators hs = table.houseSignificators(bhava);
            for (Graha agent : table.nodeAgency(node).agents()) {
                assertThat(hs.stepsFor(agent))
                        .as("%s agent %s merged into bhava %d", node, agent, bhava)
                        .contains(Step.OCCUPANT);
            }
        }
    }

    @Test
    void nodeAgencyRejectsNonNodes() {
        Map<Graha, Double> lons = new EnumMap<>(Graha.class);
        double d = 3.0;
        for (Graha g : Graha.values()) {
            lons.put(g, d);
            d += 40.0;
        }
        SignificatorTable table = SignificatorTable.of(chart(lons));
        assertThatThrownBy(() -> table.nodeAgency(Graha.SUN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> table.nodeAgency(Graha.JUPITER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
