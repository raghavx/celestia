package com.celestia.geo;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.geo.place.PlaceCandidate;
import com.celestia.geo.time.TimeshapeTimeZoneResolver;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * SC-005 — a warm {@code resolve(...)} completes well under budget. Target 20 ms;
 * the guard is looser to stay CI-stable. The {@code timeshape} engine build is a
 * one-time cost and is warmed before timing. {@code @Tag("perf")} — excluded from
 * the default run.
 */
@Tag("perf")
class BirthMomentResolverPerformanceTest {

    @Test
    void warmResolveIsFast() {
        BirthMomentResolver resolver = new BirthMomentResolver(new TimeshapeTimeZoneResolver());
        PlaceCandidate pune = new PlaceCandidate("id", "Pune", "India", "MH", 18.5204, 73.8567, "fixture");
        LocalDate date = LocalDate.of(1985, 6, 15);
        LocalTime time = LocalTime.of(14, 30);

        for (int i = 0; i < 200; i++) {
            resolver.resolve(pune, date, time, null);
        }

        long start = System.nanoTime();
        int n = 500;
        for (int i = 0; i < n; i++) {
            resolver.resolve(pune, date, time, null);
        }
        Duration perCall = Duration.ofNanos((System.nanoTime() - start) / n);

        assertThat(perCall).isLessThan(Duration.ofMillis(20));
    }
}
