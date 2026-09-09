package com.celestia.core.judgement;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.chart.NatalChartFactory;
import com.celestia.core.golden.GoldenChart;
import com.celestia.ephemeris.BirthData;
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
 * SPEC-003 SC-001: every golden chart reproduces the twelve per-house
 * significator lists (graha order and step tags) from the cross-checked
 * reference. SC-003: Rahu / Ketu agency matches.
 */
@EnabledIf("hasEphemerisData")
class SignificatorGoldenTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @TestFactory
    List<DynamicNode> everyGoldenChartReproducesItsSignificators() {
        var factory = new NatalChartFactory(
                new SwissEphemerisPositionProvider(), new SwissEphemerisHouseProvider());
        List<DynamicNode> byChart = new ArrayList<>();

        for (GoldenChart chart : GoldenChart.loadAll()) {
            NatalChart natal = factory.cast(
                    new BirthData(chart.instant(), chart.latitude(), chart.longitude()));
            SignificatorTable table = SignificatorTable.of(natal);
            List<DynamicNode> checks = new ArrayList<>();

            for (int h = 1; h <= 12; h++) {
                int house = h;
                List<Significator> actual = table.houseSignificators(house).significators();
                List<GoldenChart.SigRef> expected = chart.significatorsByHouse().get(house);
                checks.add(DynamicTest.dynamicTest("house " + house + " significators", () -> {
                    assertThat(actual.stream().map(s -> s.graha().name()).toList())
                            .as("house %d graha order", house)
                            .isEqualTo(expected.stream().map(GoldenChart.SigRef::graha).toList());
                    for (int i = 0; i < expected.size(); i++) {
                        Set<Integer> ranks = actual.get(i).steps().stream()
                                .map(Step::rank).collect(Collectors.toSet());
                        assertThat(ranks)
                                .as("house %d %s steps", house, expected.get(i).graha())
                                .isEqualTo(expected.get(i).steps());
                    }
                }));
            }

            checks.add(DynamicTest.dynamicTest("per-graha transpose", () -> {
                for (var g : com.celestia.ephemeris.Graha.values()) {
                    var refHouses = chart.significatorsByGraha().get(g);
                    var gs = table.grahaSignificators(g);
                    for (var e : refHouses.entrySet()) {
                        Set<Integer> ranks = gs.stepsFor(e.getKey()).stream()
                                .map(Step::rank).collect(Collectors.toSet());
                        assertThat(ranks).as("%s / house %d", g, e.getKey()).isEqualTo(e.getValue());
                    }
                    assertThat(gs.houses().keySet())
                            .as("%s signified houses", g)
                            .isEqualTo(refHouses.keySet());
                }
            }));

            for (var node : List.of(
                    com.celestia.ephemeris.Graha.RAHU, com.celestia.ephemeris.Graha.KETU)) {
                GoldenChart.NodeAgencyRef ref = chart.nodeAgency().get(node);
                NodeAgency na = table.nodeAgency(node);
                checks.add(DynamicTest.dynamicTest(node.name() + " agency", () -> {
                    assertThat(na.signLord().name()).isEqualTo(ref.signLord());
                    assertThat(na.starLord().name()).isEqualTo(ref.starLord());
                    assertThat(na.conjunctGrahas().stream().map(Enum::name).collect(Collectors.toSet()))
                            .isEqualTo(Set.copyOf(ref.conjunctGrahas()));
                    assertThat(na.agents().stream().map(Enum::name).collect(Collectors.toSet()))
                            .isEqualTo(Set.copyOf(ref.agents()));
                }));
            }

            byChart.add(DynamicContainer.dynamicContainer(chart.id(), checks));
        }
        return byChart;
    }
}
