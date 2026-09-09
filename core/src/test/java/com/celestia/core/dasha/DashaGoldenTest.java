package com.celestia.core.dasha;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.golden.GoldenChart;
import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * SPEC-004 SC-001: every golden chart reproduces its birth Mahadasha lord exactly
 * and its balance within one day. Plus FR-016: a birth outside the ephemeris range
 * still yields a timeline, flagged {@code REDUCED}.
 */
@EnabledIf("hasEphemerisData")
class DashaGoldenTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @TestFactory
    List<DynamicNode> everyGoldenChartReproducesItsBalance() {
        var factory = new DashaTimelineFactory(new SwissEphemerisPositionProvider());
        List<DynamicNode> byChart = new ArrayList<>();

        for (GoldenChart chart : GoldenChart.loadAll()) {
            GoldenChart.DashaRef ref = chart.dasha();
            DashaTimeline timeline = factory.at(
                    new BirthData(chart.instant(), chart.latitude(), chart.longitude()));
            DashaBalance balance = timeline.balanceAtBirth();

            byChart.add(DynamicContainer.dynamicContainer(chart.id(), List.of(
                    DynamicTest.dynamicTest("birth Maha lord", () ->
                            assertThat(balance.mahaLord().name()).isEqualTo(ref.mahaLord())),
                    DynamicTest.dynamicTest("balance within 1 day", () ->
                            assertThat(balance.balance().toSeconds() / 86_400.0)
                                    .isCloseTo(ref.balanceDays(), org.assertj.core.data.Offset.offset(1.0))))));
        }
        return byChart;
    }

    @TestFactory
    DynamicNode outOfRangeBirthDegradesToReduced() {
        return DynamicTest.dynamicTest("year-1600 birth -> REDUCED, no exception (FR-016)", () -> {
            var factory = new DashaTimelineFactory(new SwissEphemerisPositionProvider());
            DashaTimeline timeline = factory.at(
                    new BirthData(Instant.parse("1600-03-21T06:00:00Z"), 28.6139, 77.209));
            assertThat(timeline.accuracy()).isEqualTo(Accuracy.REDUCED);
            assertThat(timeline.balanceAtBirth()).isNotNull();
        });
    }
}
