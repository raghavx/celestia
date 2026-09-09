package com.celestia.core.judgement;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.chart.NatalChartFactory;
import com.celestia.core.golden.GoldenChart;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * SPEC-003 US3: each golden chart reproduces {@code expected.node_agency}, and
 * every agent is merged into the node's own bhava significators (FR-008).
 */
@EnabledIf("hasEphemerisData")
class NodeAgencyGoldenTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @TestFactory
    List<DynamicNode> everyGoldenChartReproducesNodeAgency() {
        var factory = new NatalChartFactory(
                new SwissEphemerisPositionProvider(), new SwissEphemerisHouseProvider());
        List<DynamicNode> byChart = new ArrayList<>();

        for (GoldenChart chart : GoldenChart.loadAll()) {
            NatalChart natal = factory.cast(
                    new BirthData(chart.instant(), chart.latitude(), chart.longitude()));
            SignificatorTable table = SignificatorTable.of(natal);
            List<DynamicNode> checks = new ArrayList<>();

            for (Graha node : List.of(Graha.RAHU, Graha.KETU)) {
                GoldenChart.NodeAgencyRef ref = chart.nodeAgency().get(node);
                NodeAgency na = table.nodeAgency(node);
                int bhava = natal.placement(node).bhava();

                checks.add(DynamicTest.dynamicTest(node.name() + " agency == golden", () -> {
                    assertThat(na.signLord().name()).as("sign lord").isEqualTo(ref.signLord());
                    assertThat(na.starLord().name()).as("star lord").isEqualTo(ref.starLord());
                    assertThat(na.conjunctGrahas().stream().map(Enum::name).collect(Collectors.toSet()))
                            .as("conjunct grahas")
                            .isEqualTo(Set.copyOf(ref.conjunctGrahas()));
                    assertThat(na.agents().stream().map(Enum::name).collect(Collectors.toSet()))
                            .as("agents")
                            .isEqualTo(Set.copyOf(ref.agents()));
                }));

                checks.add(DynamicTest.dynamicTest(node.name() + " agents merged into bhava " + bhava, () -> {
                    HouseSignificators hs = table.houseSignificators(bhava);
                    for (Graha agent : na.agents()) {
                        assertThat(hs.stepsFor(agent))
                                .as("agent %s is a step-2 occupant of bhava %d", agent, bhava)
                                .contains(Step.OCCUPANT);
                    }
                    // the node itself is a step-2 occupant of its own bhava
                    assertThat(table.grahaSignificators(node).stepsFor(bhava)).contains(Step.OCCUPANT);
                }));
            }
            byChart.add(DynamicContainer.dynamicContainer(chart.id(), checks));
        }
        return byChart;
    }
}
