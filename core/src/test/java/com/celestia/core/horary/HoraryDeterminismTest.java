package com.celestia.core.horary;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.golden.GoldenChart;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHoraryHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisSunriseProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** SPEC-005 US5: the 249 map and every cast are reproducible. */
class HoraryDeterminismTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @Test
    void the249MapIsStable() {
        assertThat(Horary249.arcs()).isEqualTo(Horary249.arcs());
        for (int n = 1; n <= 249; n++) {
            assertThat(Horary249.arc(n)).isEqualTo(Horary249.arc(n));
        }
    }

    @Test
    @EnabledIf("hasEphemerisData")
    void castingAndRulingPlanetsAreReproducible() {
        GoldenChart.HoraryRef ref = null;
        for (GoldenChart chart : GoldenChart.loadAll()) {
            if (chart.horary() != null) {
                ref = chart.horary();
            }
        }
        BirthData judgment = new BirthData(ref.judgmentInstant(), ref.latitude(), ref.longitude());

        var factory = new HoraryChartFactory(
                new SwissEphemerisPositionProvider(), new SwissEphemerisHoraryHouseProvider());
        assertThat(factory.cast(ref.number(), judgment)).isEqualTo(factory.cast(ref.number(), judgment));

        var positions = new SwissEphemerisPositionProvider();
        var sunrise = new SwissEphemerisSunriseProvider();
        assertThat(HoraryRulingPlanets.at(ref.number(), judgment, positions, sunrise))
                .isEqualTo(HoraryRulingPlanets.at(ref.number(), judgment, positions, sunrise));
    }
}
