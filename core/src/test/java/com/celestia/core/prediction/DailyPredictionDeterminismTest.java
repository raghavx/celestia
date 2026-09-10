package com.celestia.core.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.chart.NatalChartFactory;
import com.celestia.core.golden.GoldenChart;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EphemerisResult;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** SPEC-006 US5: the daily reading is reproducible. */
@EnabledIf("hasEphemerisData")
class DailyPredictionDeterminismTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @Test
    void computeAndPredictAreReproducibleOnTheGoldenCase() {
        GoldenChart chart = GoldenChart.loadAll().stream()
                .filter(c -> c.daily() != null).findFirst().orElseThrow();
        GoldenChart.DailyRef ref = chart.daily();

        var positions = new SwissEphemerisPositionProvider();
        NatalChart natal = new NatalChartFactory(positions, new SwissEphemerisHouseProvider())
                .cast(new BirthData(chart.instant(), chart.latitude(), chart.longitude()));

        Instant t = ref.referenceInstant();
        EphemerisResult atRef = positions.positions(t);
        EphemerisResult before = positions.positions(t.minus(Duration.ofHours(12)));
        EphemerisResult after = positions.positions(t.plus(Duration.ofHours(12)));
        assertThat(DailyPredictionFactory.compute(natal, t, atRef, before, after))
                .isEqualTo(DailyPredictionFactory.compute(natal, t, atRef, before, after));

        var factory = new DailyPredictionFactory(positions);
        LocalDate date = ref.date();
        assertThat(factory.predict(natal, date, ref.longitude()))
                .isEqualTo(factory.predict(natal, date, ref.longitude()));
    }
}
