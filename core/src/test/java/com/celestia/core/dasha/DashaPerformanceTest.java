package com.celestia.core.dasha;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.EngineVersion;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * SPEC-004 SC-005: the depth-5 running stack for a date, well under 50 ms warm
 * (target 5 ms). Machine-dependent — {@code @Tag("perf")}, excluded from the
 * default run (opt in with {@code -Pperf}).
 */
@Tag("perf")
class DashaPerformanceTest {

    @Test
    void depthFiveRunningStackIsFastEnough() {
        DashaTimeline timeline = DashaTimeline.from(
                Instant.parse("1961-08-05T05:24:00Z"), 123.456, Accuracy.FULL,
                new EngineVersion(EngineVersion.RULES, "perf", "perf", "swieph"));
        Instant query = Instant.parse("2005-01-01T00:00:00Z");

        for (int i = 0; i < 500; i++) {
            timeline.running(query, 5);
        }
        long start = System.nanoTime();
        int reps = 5000;
        for (int i = 0; i < reps; i++) {
            timeline.running(query.plusSeconds(i), 5);
        }
        double perCallMs = (System.nanoTime() - start) / 1_000_000.0 / reps;
        assertThat(perCallMs).as("ms per depth-5 running stack").isLessThan(50.0);
    }
}
