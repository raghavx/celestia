package com.celestia.core.judgement;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.chart.NatalChart;
import com.celestia.ephemeris.Graha;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * SPEC-003 SC-005: the full significator table (12 houses + per-graha transpose +
 * node agency) well under 200 ms warm (target 20 ms). Machine-dependent —
 * {@code @Tag("perf")}, excluded from the default run (opt in with {@code -Pperf}).
 */
@Tag("perf")
class SignificatorPerformanceTest {

    @Test
    void fullTableIsFastEnoughForARequestPath() {
        Map<Graha, Double> lons = new EnumMap<>(Graha.class);
        double d = 7.5;
        for (Graha g : Graha.values()) {
            lons.put(g, d);
            d += 39.3;
        }
        NatalChart chart = SyntheticChart.of(lons, SyntheticChart.equalCusps(12.0));

        for (int i = 0; i < 200; i++) {
            exercise(chart);
        }
        long start = System.nanoTime();
        int reps = 2000;
        for (int i = 0; i < reps; i++) {
            exercise(chart);
        }
        double perCallMs = (System.nanoTime() - start) / 1_000_000.0 / reps;
        assertThat(perCallMs).as("ms per full significator table").isLessThan(200.0);
    }

    private static void exercise(NatalChart chart) {
        SignificatorTable table = SignificatorTable.of(chart);
        for (int h = 1; h <= 12; h++) {
            table.houseSignificators(h);
        }
        for (Graha g : Graha.values()) {
            table.grahaSignificators(g);
        }
        table.nodeAgency(Graha.RAHU);
        table.nodeAgency(Graha.KETU);
    }
}
