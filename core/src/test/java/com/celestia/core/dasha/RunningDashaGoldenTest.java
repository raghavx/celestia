package com.celestia.core.dasha;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.golden.GoldenChart;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * SPEC-004 SC-002: for each golden chart, the five running period lords at
 * {@code birth + 40 Julian years} match the reference exactly, and the Mahadasha
 * and Antardasha boundaries match within one day.
 */
@EnabledIf("hasEphemerisData")
class RunningDashaGoldenTest {

    private static final Duration FORTY_YEARS = Duration.ofSeconds(40L * (365L * 86_400L + 21_600L));

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @TestFactory
    List<DynamicNode> everyGoldenChartReproducesItsRunningStack() {
        var factory = new DashaTimelineFactory(new SwissEphemerisPositionProvider());
        List<DynamicNode> byChart = new ArrayList<>();

        for (GoldenChart chart : GoldenChart.loadAll()) {
            GoldenChart.DashaRef ref = chart.dasha();
            DashaTimeline timeline = factory.at(
                    new BirthData(chart.instant(), chart.latitude(), chart.longitude()));
            Instant query = chart.instant().plus(FORTY_YEARS);
            RunningDasha running = timeline.running(query, 5);

            List<DynamicNode> checks = new ArrayList<>();
            checks.add(DynamicTest.dynamicTest("query instant", () ->
                    assertThat(query).isEqualTo(ref.runningQueryUtc())));
            checks.add(DynamicTest.dynamicTest("five lords exact", () ->
                    assertThat(running.stack().stream().map(p -> p.lord().name()).toList())
                            .isEqualTo(ref.runningLords())));
            for (GoldenChart.DashaPeriodRef pref : ref.runningPeriods()) {
                if (pref.level().equals("MAHADASHA") || pref.level().equals("ANTARDASHA")) {
                    DashaLevel level = DashaLevel.valueOf(pref.level());
                    DashaPeriod actual = running.period(level);
                    checks.add(DynamicTest.dynamicTest(pref.level() + " boundaries within 1 day", () -> {
                        assertThat(secondsBetween(actual.start(), pref.start())).isCloseTo(0.0, Offset.offset(86_400.0));
                        assertThat(secondsBetween(actual.end(), pref.end())).isCloseTo(0.0, Offset.offset(86_400.0));
                    }));
                }
            }
            byChart.add(DynamicContainer.dynamicContainer(chart.id(), checks));
        }
        return byChart;
    }

    private static double secondsBetween(Instant a, Instant b) {
        return Duration.between(a, b).toNanos() / 1e9;
    }
}
