package com.celestia.core.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.lordage.KpLordage;
import com.celestia.core.lordage.LordChain;
import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.EphemerisResult;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.GrahaPosition;
import com.celestia.ephemeris.PositionProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
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
 * SC-001 / SC-002: for every golden chart, the engine reproduces every published
 * value. Positions are checked against the independent pyswisseph reference; the
 * full lord chain must match exactly.
 *
 * <p>Requires the {@code .se1} data (full accuracy) - run {@code scripts/fetch-ephe.sh}.
 */
@EnabledIf("hasEphemerisData")
class GoldenChartTest {

    /** engine (SE port 2.01) vs reference (pyswisseph 2.10): sub-arcsecond in practice. */
    private static final Offset<Double> LONGITUDE_TOLERANCE_DEG = Offset.offset(3.0 / 3600.0);

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @TestFactory
    List<DynamicNode> everyGoldenChartReproducesEveryPublishedValue() {
        PositionProvider provider = new SwissEphemerisPositionProvider();
        List<DynamicNode> byChart = new ArrayList<>();

        for (GoldenChart chart : GoldenChart.loadAll()) {
            EphemerisResult result = provider.positions(chart.instant());
            List<DynamicTest> grahaChecks = new ArrayList<>();

            for (Graha g : Graha.values()) {
                GoldenChart.Expected e = chart.expected().get(g);
                GrahaPosition pos = result.position(g);
                grahaChecks.add(DynamicTest.dynamicTest(g.name(), () -> {
                    assertThat(pos.accuracy()).isEqualTo(Accuracy.FULL);
                    assertThat(pos.longitude())
                            .as("sidereal longitude of %s [engine %s]", g, result.engineVersion().id())
                            .isCloseTo(e.longitude(), LONGITUDE_TOLERANCE_DEG);
                    assertThat(pos.retrograde()).as("retrograde flag of %s", g).isEqualTo(e.retrograde());

                    LordChain c = KpLordage.chainFor(pos.longitude());
                    assertThat(c.sign().name()).as("sign of %s", g).isEqualTo(e.sign());
                    assertThat(c.signLord().name()).as("sign lord of %s", g).isEqualTo(e.signLord());
                    assertThat(c.nakshatra().name()).as("nakshatra of %s", g).isEqualTo(e.nakshatra());
                    assertThat(c.pada()).as("pada of %s", g).isEqualTo(e.pada());
                    assertThat(c.starLord().name()).as("star lord of %s", g).isEqualTo(e.starLord());
                    assertThat(c.subLord().name()).as("sub lord of %s", g).isEqualTo(e.subLord());
                    assertThat(c.subSubLord().name()).as("sub-sub lord of %s", g).isEqualTo(e.subSubLord());
                }));
            }
            byChart.add(DynamicContainer.dynamicContainer(chart.id(), grahaChecks));
        }
        return byChart;
    }
}
