package com.celestia.core.dasha;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.EngineVersion;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

/**
 * SPEC-004 SC-003: at every level the nine child periods partition the parent —
 * contiguous, no gap, no overlap, exactly covering it.
 */
class DashaPartitionPropertyTest {

    private static final EngineVersion ENGINE =
            new EngineVersion(EngineVersion.RULES, "prop", "prop", "swieph");
    private static final long YEAR_SECONDS = 365L * 86_400L + 21_600L;

    @Property(tries = 400)
    void everyResolvedPeriodIsExactlyTiledByItsNineChildren(
            @ForAll @LongRange(min = -40_000L, max = 40_000L) long birthEpochDay,
            @ForAll @DoubleRange(min = 0.0, max = 360.0, maxIncluded = false) double moonLongitude,
            @ForAll @IntRange(min = 21, max = 300) int yearsAfterBirth) {

        Instant birth = Instant.EPOCH.plus(Duration.ofDays(birthEpochDay));
        DashaTimeline timeline = DashaTimeline.from(birth, moonLongitude, Accuracy.FULL, ENGINE);
        // >= 21 years after birth => every enclosing period starts at/after birth
        Instant query = birth.plus(Duration.ofSeconds((long) yearsAfterBirth * YEAR_SECONDS));

        for (int rank = 1; rank <= 4; rank++) {
            DashaPeriod parent = timeline.running(query, rank).period(DashaLevel.ofRank(rank));
            List<DashaPeriod> children = timeline.periods(
                    DashaLevel.ofRank(rank + 1), parent.start(), parent.end().minusNanos(1));

            assertThat(children).as("nine children of %s", parent.level()).hasSize(9);
            assertThat(children.get(0).start()).isEqualTo(parent.start());
            assertThat(children.get(8).end()).isEqualTo(parent.end());
            for (int i = 1; i < 9; i++) {
                assertThat(children.get(i).start())
                        .as("child %d starts where child %d ends", i, i - 1)
                        .isEqualTo(children.get(i - 1).end());
            }
        }
    }
}
