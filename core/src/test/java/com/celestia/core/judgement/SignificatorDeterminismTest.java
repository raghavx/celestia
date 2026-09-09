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
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * SPEC-003 SC-005: {@code SignificatorTable.of} is reproducible. The table is a
 * class without {@code equals}, so we compare the accessor outputs (records with
 * value equality).
 */
class SignificatorDeterminismTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    private static void assertSameTable(SignificatorTable a, SignificatorTable b) {
        assertThat(a.engineVersion()).isEqualTo(b.engineVersion());
        for (int h = 1; h <= 12; h++) {
            assertThat(a.houseSignificators(h)).isEqualTo(b.houseSignificators(h));
        }
        for (Graha g : Graha.values()) {
            assertThat(a.grahaSignificators(g)).isEqualTo(b.grahaSignificators(g));
        }
        for (Graha node : new Graha[] {Graha.RAHU, Graha.KETU}) {
            assertThat(a.nodeAgency(node)).isEqualTo(b.nodeAgency(node));
        }
    }

    @Test
    void syntheticChartIsReproducible() {
        Map<Graha, Double> lons = new EnumMap<>(Graha.class);
        double d = 13.0;
        for (Graha g : Graha.values()) {
            lons.put(g, d);
            d += 38.7;
        }
        NatalChart chart = SyntheticChart.of(lons, SyntheticChart.equalCusps(9.0));
        assertSameTable(SignificatorTable.of(chart), SignificatorTable.of(chart));
    }

    @Test
    @EnabledIf("hasEphemerisData")
    void everyGoldenChartTableIsReproducible() {
        var factory = new NatalChartFactory(
                new SwissEphemerisPositionProvider(), new SwissEphemerisHouseProvider());
        for (GoldenChart gc : GoldenChart.loadAll()) {
            NatalChart natal = factory.cast(
                    new BirthData(gc.instant(), gc.latitude(), gc.longitude()));
            assertSameTable(SignificatorTable.of(natal), SignificatorTable.of(natal));
        }
    }
}
