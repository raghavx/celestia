package com.celestia.core.chart;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.golden.GoldenChart;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * SPEC-002 SC-001 / SC-002: every golden chart reproduces every cusp longitude,
 * cuspal sub lord, bhava and rasi house.
 */
@EnabledIf("hasEphemerisData")
class NatalChartGoldenTest {

    /** engine (SE 2.01) vs reference (pyswisseph 2.10): sub-arcsecond; 1′ is generous. */
    private static final Offset<Double> CUSP_TOLERANCE = Offset.offset(1.0 / 60.0);

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @TestFactory
    List<DynamicNode> everyGoldenChartReproducesEveryHouseValue() {
        var factory = new NatalChartFactory(
                new SwissEphemerisPositionProvider(), new SwissEphemerisHouseProvider());
        List<DynamicNode> byChart = new ArrayList<>();

        for (GoldenChart chart : GoldenChart.loadAll()) {
            NatalChart natal = factory.cast(
                    new BirthData(chart.instant(), chart.latitude(), chart.longitude()));
            List<DynamicNode> checks = new ArrayList<>();

            for (GoldenChart.CuspRef ref : chart.cusps()) {
                Cusp cusp = natal.cusp(ref.house());
                checks.add(DynamicTest.dynamicTest("cusp " + ref.house(), () -> {
                    assertThat(cusp.longitude())
                            .as("cusp %d longitude [engine %s]", ref.house(), natal.engineVersion().id())
                            .isCloseTo(ref.chain().longitude(), CUSP_TOLERANCE);
                    assertThat(cusp.subLord().name()).as("cusp %d sub lord", ref.house())
                            .isEqualTo(ref.chain().subLord());
                    assertThat(cusp.lordChain().sign().name()).isEqualTo(ref.chain().sign());
                    assertThat(cusp.lordChain().nakshatra().name()).isEqualTo(ref.chain().nakshatra());
                    assertThat(cusp.lordChain().subSubLord().name()).isEqualTo(ref.chain().subSubLord());
                }));
            }

            for (Graha g : Graha.values()) {
                GoldenChart.Expected e = chart.expected().get(g);
                HousePlacement p = natal.placement(g);
                checks.add(DynamicTest.dynamicTest(g.name() + " placement", () -> {
                    assertThat(p.bhava()).as("bhava of %s", g).isEqualTo(e.bhava());
                    assertThat(p.rasiHouse()).as("rasi house of %s", g).isEqualTo(e.rasiHouse());
                }));
            }
            byChart.add(DynamicContainer.dynamicContainer(chart.id(), checks));
        }
        return byChart;
    }
}
