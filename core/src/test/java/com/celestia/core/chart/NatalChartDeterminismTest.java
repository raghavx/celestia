package com.celestia.core.chart;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.golden.GoldenChart;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** SPEC-002 SC-005: casting the same chart twice yields an equal NatalChart. */
@EnabledIf("hasEphemerisData")
class NatalChartDeterminismTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @Test
    void castingIsDeterministicAndCuspOneIsBitIdenticalToTheAscendant() {
        var factory = new NatalChartFactory(
                new SwissEphemerisPositionProvider(), new SwissEphemerisHouseProvider());

        for (GoldenChart gc : GoldenChart.loadAll()) {
            BirthData bd = new BirthData(gc.instant(), gc.latitude(), gc.longitude());
            NatalChart a = factory.cast(bd);
            NatalChart b = factory.cast(bd);
            assertThat(a).as("%s cast twice", gc.id()).isEqualTo(b);
            assertThat(Double.compare(a.cusp(1).longitude(), a.ascendant().longitude())).isZero();
        }
    }
}
