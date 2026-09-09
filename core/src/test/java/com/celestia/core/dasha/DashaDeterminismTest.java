package com.celestia.core.dasha;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.golden.GoldenChart;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * SPEC-004 SC-005: {@link DashaTimeline} is reproducible. The timeline is a class
 * without {@code equals}, so we compare the accessor outputs (records with value
 * equality).
 */
@EnabledIf("hasEphemerisData")
class DashaDeterminismTest {

    private static final Duration FORTY_YEARS = Duration.ofSeconds(40L * (365L * 86_400L + 21_600L));

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @Test
    void everyGoldenChartTimelineIsReproducible() {
        var factory = new DashaTimelineFactory(new SwissEphemerisPositionProvider());
        for (GoldenChart chart : GoldenChart.loadAll()) {
            var birth = new BirthData(chart.instant(), chart.latitude(), chart.longitude());
            DashaTimeline a = factory.at(birth);
            DashaTimeline b = factory.at(birth);

            assertThat(a.balanceAtBirth()).isEqualTo(b.balanceAtBirth());

            Instant q = chart.instant().plus(FORTY_YEARS);
            assertThat(a.running(q, 5)).isEqualTo(b.running(q, 5));

            Instant from = chart.instant().plus(Duration.ofDays(400));
            Instant to = from.plus(Duration.ofDays(4000));
            assertThat(a.periods(DashaLevel.ANTARDASHA, from, to))
                    .isEqualTo(b.periods(DashaLevel.ANTARDASHA, from, to));
        }
    }
}
