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

/** SPEC-003 SC-002: each golden chart reproduces {@code expected.significators.by_graha}. */
@EnabledIf("hasEphemerisData")
class GrahaSignificatorGoldenTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @TestFactory
    List<DynamicNode> everyGoldenChartReproducesItsPerGrahaTable() {
        var factory = new NatalChartFactory(
                new SwissEphemerisPositionProvider(), new SwissEphemerisHouseProvider());
        List<DynamicNode> byChart = new ArrayList<>();

        for (GoldenChart chart : GoldenChart.loadAll()) {
            NatalChart natal = factory.cast(
                    new BirthData(chart.instant(), chart.latitude(), chart.longitude()));
            SignificatorTable table = SignificatorTable.of(natal);
            List<DynamicNode> checks = new ArrayList<>();

            for (Graha g : Graha.values()) {
                var expected = chart.significatorsByGraha().get(g);
                GrahaSignificators gs = table.grahaSignificators(g);
                checks.add(DynamicTest.dynamicTest(g.name(), () -> {
                    assertThat(gs.houses().keySet())
                            .as("%s signified houses", g)
                            .isEqualTo(expected.keySet());
                    for (var e : expected.entrySet()) {
                        Set<Integer> ranks = gs.stepsFor(e.getKey()).stream()
                                .map(Step::rank).collect(Collectors.toSet());
                        assertThat(ranks).as("%s / house %d steps", g, e.getKey())
                                .isEqualTo(e.getValue());
                    }
                }));
            }
            byChart.add(DynamicContainer.dynamicContainer(chart.id(), checks));
        }
        return byChart;
    }
}
