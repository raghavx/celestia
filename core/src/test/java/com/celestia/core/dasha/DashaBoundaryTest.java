package com.celestia.core.dasha;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.Graha;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * SPEC-004 SC-004: the running stack respects the half-open {@code [start, end)}
 * boundary at every level. Queries are offset by 1 ns from the rounded boundary
 * instant — comfortably past the sub-nanosecond rounding of an exact boundary.
 */
class DashaBoundaryTest {

    private static final Instant BIRTH = Instant.parse("2000-01-01T00:00:00Z");
    private static final EngineVersion ENGINE =
            new EngineVersion(EngineVersion.RULES, "test", "test", "swieph");

    // Moon at 20° -> Venus Mahadasha, ending exactly BIRTH + 10 years
    private final DashaTimeline timeline = DashaTimeline.from(BIRTH, 20.0, Accuracy.FULL, ENGINE);

    @Test
    void theMahadashaBoundaryIsHalfOpen() {
        Instant boundary = timeline.balanceAtBirth().mahaEnd();
        assertThat(timeline.running(boundary.minusNanos(1), 1).lord(DashaLevel.MAHADASHA))
                .isEqualTo(Graha.VENUS);
        assertThat(timeline.running(boundary.plusNanos(1), 1).lord(DashaLevel.MAHADASHA))
                .isEqualTo(Graha.VENUS.next()); // -> SUN
    }

    @Test
    void atAFreshMahadashaStartEveryDeeperLevelIsThatMahaLord() {
        Instant boundary = timeline.balanceAtBirth().mahaEnd();
        RunningDasha r = timeline.running(boundary.plusNanos(1), 5);
        Graha maha = r.lord(DashaLevel.MAHADASHA);
        for (DashaLevel level : DashaLevel.values()) {
            assertThat(r.lord(level)).as("%s lord at a fresh Maha start", level).isEqualTo(maha);
            assertThat(r.period(level).start())
                    .isEqualTo(r.period(DashaLevel.MAHADASHA).start());
        }
    }

    @Test
    void aDeepPeriodStartReturnsThatPeriodNotItsPredecessor() {
        // well after birth, so the deep period's start is comfortably post-birth
        RunningDasha ref = timeline.running(BIRTH.plus(Duration.ofDays(1000)), 5);
        DashaPeriod prana = ref.period(DashaLevel.PRANA);

        DashaPeriod atStart = timeline.running(prana.start().plusNanos(1), 5).period(DashaLevel.PRANA);
        assertThat(atStart.lord()).isEqualTo(prana.lord());
        assertThat(atStart.start()).isEqualTo(prana.start());

        DashaPeriod predecessor =
                timeline.running(prana.start().minusNanos(1), 5).period(DashaLevel.PRANA);
        assertThat(predecessor.start()).isBefore(prana.start());
        assertThat(predecessor.end()).isEqualTo(prana.start());
    }
}
