package com.celestia.core.judgement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.core.chart.NatalChart;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.Graha;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Structural invariants of the four-step significator table (SPEC-003 US1/US2/US3). */
class SignificatorTableTest {

    /** A spread-out chart: one graha per 40&deg;, cusps offset so occupancy varies. */
    private static NatalChart sampleChart() {
        Map<Graha, Double> lons = new LinkedHashMap<>();
        double d = 3.0;
        for (Graha g : Graha.values()) {
            lons.put(g, d);
            d += 41.7; // irrational-ish step so star/sub lords vary
        }
        return SyntheticChart.of(lons, SyntheticChart.equalCusps(17.0));
    }

    @Test
    void everyHouseIsOrderedDeDupedAndOwnerBearing() {
        SignificatorTable table = SignificatorTable.of(sampleChart());
        NatalChart chart = sampleChart();

        for (int h = 1; h <= 12; h++) {
            HouseSignificators hs = table.houseSignificators(h);

            // de-duped by graha
            assertThat(hs.significators().stream().map(Significator::graha).distinct().count())
                    .isEqualTo((long) hs.significators().size());

            // ordered by BY_STRENGTH
            List<Significator> sorted = hs.significators().stream()
                    .sorted(Significator.BY_STRENGTH)
                    .toList();
            assertThat(hs.significators()).containsExactlyElementsOf(sorted);

            // the house owner is always a significator, tagged OWNER
            Graha owner = chart.cusp(h).lordChain().signLord();
            assertThat(hs.signifies(owner)).as("house %d owner %s present", h, owner).isTrue();
            assertThat(hs.stepsFor(owner)).contains(Step.OWNER);

            // every step set is non-empty and every house number matches
            for (Significator s : hs.significators()) {
                assertThat(s.steps()).isNotEmpty();
                assertThat(s.house()).isEqualTo(h);
            }
        }
    }

    @Test
    void occupantsAreTaggedOccupant() {
        NatalChart chart = sampleChart();
        SignificatorTable table = SignificatorTable.of(chart);
        for (Graha g : Graha.values()) {
            int bhava = chart.placement(g).bhava();
            assertThat(table.houseSignificators(bhava).stepsFor(g))
                    .as("%s occupies house %d", g, bhava)
                    .contains(Step.OCCUPANT);
        }
    }

    @Test
    void perGrahaTableIsTheStrictTransposeOfPerHouse() {
        SignificatorTable table = SignificatorTable.of(sampleChart());
        for (Graha g : Graha.values()) {
            GrahaSignificators gs = table.grahaSignificators(g);
            for (int h = 1; h <= 12; h++) {
                assertThat(gs.stepsFor(h))
                        .as("transpose %s / house %d", g, h)
                        .isEqualTo(table.houseSignificators(h).stepsFor(g));
            }
        }
    }

    @Test
    void nodeAgencyAgentsAreConjunctPlusSignAndStarLords() {
        NatalChart chart = sampleChart();
        SignificatorTable table = SignificatorTable.of(chart);

        for (Graha node : List.of(Graha.RAHU, Graha.KETU)) {
            NodeAgency na = table.nodeAgency(node);
            assertThat(na.node()).isEqualTo(node);
            assertThat(na.agents())
                    .containsAll(na.conjunctGrahas())
                    .contains(na.signLord(), na.starLord());
            // conjunct grahas actually share the node's bhava and are non-nodes
            int nodeBhava = chart.placement(node).bhava();
            for (Graha c : na.conjunctGrahas()) {
                assertThat(c).isNotIn(Graha.RAHU, Graha.KETU);
                assertThat(chart.placement(c).bhava()).isEqualTo(nodeBhava);
            }
        }
    }

    @Test
    void nodeAgencyRejectsNonNodes() {
        SignificatorTable table = SignificatorTable.of(sampleChart());
        assertThatThrownBy(() -> table.nodeAgency(Graha.SUN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void houseAccessorRejectsOutOfRange() {
        SignificatorTable table = SignificatorTable.of(sampleChart());
        assertThatThrownBy(() -> table.houseSignificators(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> table.houseSignificators(13))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void carriesTheChartEngineVersion() {
        NatalChart chart = sampleChart();
        assertThat(SignificatorTable.of(chart).engineVersion())
                .isEqualTo(chart.engineVersion())
                .extracting(EngineVersion::rules)
                .isEqualTo(EngineVersion.RULES);
    }

    @Test
    void nodeInAHouseFoldsItsAgentsIntoThatHousesEffectiveOccupants() {
        NatalChart chart = sampleChart();
        SignificatorTable table = SignificatorTable.of(chart);

        for (Graha node : List.of(Graha.RAHU, Graha.KETU)) {
            int bhava = chart.placement(node).bhava();
            HouseSignificators hs = table.houseSignificators(bhava);
            for (Graha agent : table.nodeAgency(node).agents()) {
                assertThat(hs.stepsFor(agent))
                        .as("agent %s of %s counts as an effective occupant of house %d",
                                agent, node, bhava)
                        .contains(Step.OCCUPANT);
            }
        }
    }
}
