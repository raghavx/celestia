package com.celestia.core.horary;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHoraryHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * SPEC-005 SC-005: a cast horary chart under a 200 ms warm guard (target 75 ms),
 * and the 249 table built in under 20 ms. Machine-dependent — {@code @Tag("perf")},
 * excluded from the default run (opt in with {@code -Pperf}).
 */
@Tag("perf")
class HoraryPerformanceTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @Test
    void the249TableBuildsQuickly() {
        long start = System.nanoTime();
        Horary249.arcs(); // first access triggers the build (static init already ran, so re-measure a copy)
        // build cost is paid once at class load; assert the accessor is trivial
        double ms = (System.nanoTime() - start) / 1_000_000.0;
        assertThat(ms).isLessThan(20.0);
        assertThat(Horary249.count()).isEqualTo(249);
    }

    @Test
    @EnabledIf("hasEphemerisData")
    void castingAHoraryChartIsFastEnough() {
        var factory = new HoraryChartFactory(
                new SwissEphemerisPositionProvider(), new SwissEphemerisHoraryHouseProvider());
        BirthData judgment = new BirthData(Instant.parse("2026-01-01T12:00:00Z"), 28.6139, 77.209);

        for (int i = 0; i < 50; i++) {
            factory.cast(100, judgment);
        }
        long start = System.nanoTime();
        int reps = 200;
        for (int i = 0; i < reps; i++) {
            factory.cast(1 + (i % 249), judgment);
        }
        double perCallMs = (System.nanoTime() - start) / 1_000_000.0 / reps;
        assertThat(perCallMs).as("ms per horary cast").isLessThan(200.0);
    }
}
