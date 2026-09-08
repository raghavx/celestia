package com.celestia.core.chart;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * SPEC-002 SC-006: a full natal chart in under 75 ms warm. Machine-dependent —
 * {@code @Tag("perf")}, excluded from the default run (opt in with {@code -Pperf}).
 * The ceiling is a generous regression guard.
 */
@Tag("perf")
class NatalChartPerformanceTest {

    @Test
    void fullChartIsFastEnoughForARequestPath() {
        var factory = new NatalChartFactory(
                new SwissEphemerisPositionProvider(), new SwissEphemerisHouseProvider());
        BirthData bd = new BirthData(Instant.parse("1961-08-05T05:24:00Z"), 21.30694, -157.85833);

        for (int i = 0; i < 50; i++) {
            factory.cast(bd);
        }
        long start = System.nanoTime();
        int reps = 200;
        for (int i = 0; i < reps; i++) {
            factory.cast(bd);
        }
        double perCallMs = (System.nanoTime() - start) / 1_000_000.0 / reps;
        assertThat(perCallMs).as("ms per full natal chart").isLessThan(300.0);
    }
}
