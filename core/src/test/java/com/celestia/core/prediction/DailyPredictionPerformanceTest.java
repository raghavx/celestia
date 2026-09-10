package com.celestia.core.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.chart.NatalChartFactory;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EphemerisResult;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * SPEC-006 SC-005: a full reading (given a cast {@code NatalChart}) under a 150 ms
 * warm guard (target 50 ms). Machine-dependent — {@code @Tag("perf")}, excluded
 * from the default run (opt in with {@code -Pperf}).
 */
@Tag("perf")
class DailyPredictionPerformanceTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @Test
    @EnabledIf("hasEphemerisData")
    void aFullReadingIsFastEnough() {
        var positions = new SwissEphemerisPositionProvider();
        NatalChart chart = new NatalChartFactory(positions, new SwissEphemerisHouseProvider())
                .cast(new BirthData(Instant.parse("1961-08-05T05:24:00Z"), 21.30694, -157.85833));
        Instant ref = Instant.parse("2001-08-05T22:00:00Z");
        EphemerisResult at = positions.positions(ref);
        EphemerisResult before = positions.positions(ref.minus(Duration.ofHours(12)));
        EphemerisResult after = positions.positions(ref.plus(Duration.ofHours(12)));

        for (int i = 0; i < 100; i++) {
            DailyPredictionFactory.compute(chart, ref, at, before, after);
        }
        long start = System.nanoTime();
        int reps = 500;
        for (int i = 0; i < reps; i++) {
            DailyPredictionFactory.compute(chart, ref.plusSeconds(i), at, before, after);
        }
        double perCallMs = (System.nanoTime() - start) / 1_000_000.0 / reps;
        assertThat(perCallMs).as("ms per daily reading (pure compute)").isLessThan(150.0);
    }
}
