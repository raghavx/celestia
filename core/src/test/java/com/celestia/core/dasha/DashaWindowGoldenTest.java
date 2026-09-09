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
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * SPEC-004 US3: for every golden chart, enumerating the Antardashas across the
 * birth Mahadasha tiles it contiguously, and each enumerated period agrees with
 * the (golden-verified) point resolution {@link DashaTimeline#running}.
 */
@EnabledIf("hasEphemerisData")
class DashaWindowGoldenTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @TestFactory
    List<DynamicNode> everyGoldenChartEnumeratesItsBirthMahaAntardashas() {
        var factory = new DashaTimelineFactory(new SwissEphemerisPositionProvider());
        List<DynamicNode> byChart = new ArrayList<>();

        for (GoldenChart chart : GoldenChart.loadAll()) {
            DashaTimeline timeline = factory.at(
                    new BirthData(chart.instant(), chart.latitude(), chart.longitude()));
            DashaBalance b = timeline.balanceAtBirth();

            // from birth (periods() cannot start before it) to just inside the Maha end
            List<DashaPeriod> antars = timeline.periods(
                    DashaLevel.ANTARDASHA, chart.instant(), b.mahaEnd().minusNanos(1));

            byChart.add(DynamicContainer.dynamicContainer(chart.id(), List.of(
                    DynamicTest.dynamicTest("contiguous & within the birth Maha", () -> {
                        assertThat(antars).isNotEmpty();
                        assertThat(antars.get(antars.size() - 1).end())
                                .isEqualTo(b.mahaEnd());
                        for (int i = 0; i < antars.size(); i++) {
                            DashaPeriod p = antars.get(i);
                            assertThat(p.level()).isEqualTo(DashaLevel.ANTARDASHA);
                            assertThat(p.parentLords()).containsExactly(b.mahaLord());
                            assertThat(p.start()).isAfterOrEqualTo(b.mahaStart());
                            assertThat(p.end()).isBeforeOrEqualTo(b.mahaEnd());
                            if (i > 0) {
                                assertThat(antars.get(i - 1).end()).isEqualTo(p.start());
                            }
                        }
                    }),
                    DynamicTest.dynamicTest("each agrees with running(midpoint)", () -> {
                        for (DashaPeriod p : antars) {
                            // the first period straddles birth; clamp the probe to >= birth
                            Instant lo = p.start().isAfter(chart.instant()) ? p.start() : chart.instant();
                            Instant mid = lo.plus(Duration.between(lo, p.end()).dividedBy(2));
                            DashaPeriod viaRunning =
                                    timeline.running(mid, 2).period(DashaLevel.ANTARDASHA);
                            assertThat(viaRunning.lord()).isEqualTo(p.lord());
                            assertThat(viaRunning.start()).isEqualTo(p.start());
                            assertThat(viaRunning.end()).isEqualTo(p.end());
                        }
                    }))));
        }
        return byChart;
    }
}
