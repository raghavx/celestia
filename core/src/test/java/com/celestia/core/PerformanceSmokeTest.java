package com.celestia.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.lordage.KpLordage;
import com.celestia.ephemeris.EphemerisResult;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.PositionProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * SC-006: nine positions + nine lord chains for one instant, warm. Machine
 * dependent, so {@code @Tag("perf")} - excluded from the default run, opt in with
 * {@code -Pperf}. The ceiling is a generous regression guard, not the 50 ms target.
 */
@Tag("perf")
class PerformanceSmokeTest {

    @Test
    void ninePositionsPlusChainsAreFastEnoughForARequestPath() {
        PositionProvider provider = new SwissEphemerisPositionProvider();
        Instant t = Instant.parse("1961-08-05T05:24:00Z");

        for (int i = 0; i < 50; i++) {
            oneChart(provider, t);
        }

        long start = System.nanoTime();
        int reps = 200;
        for (int i = 0; i < reps; i++) {
            oneChart(provider, t);
        }
        double perCallMs = (System.nanoTime() - start) / 1_000_000.0 / reps;

        assertThat(perCallMs).as("ms per (9 positions + 9 lord chains)").isLessThan(250.0);
    }

    private static void oneChart(PositionProvider provider, Instant t) {
        EphemerisResult r = provider.positions(t);
        for (Graha g : Graha.values()) {
            KpLordage.chainFor(r.position(g).longitude());
        }
    }
}
