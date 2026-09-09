package com.celestia.core.dasha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.Graha;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** SPEC-004 US2: the running period stack. */
class RunningDashaTest {

    private static final Instant BIRTH = Instant.parse("2000-01-01T00:00:00Z");
    private static final EngineVersion ENGINE =
            new EngineVersion(EngineVersion.RULES, "test", "test", "swieph");
    private static final long YEAR_SECONDS = 365L * 86_400L + 21_600L;

    // Moon at 20° -> Bharani / Venus, elapsed fraction 0.5 (10 of 20 years)
    private final DashaTimeline timeline = DashaTimeline.from(BIRTH, 20.0, Accuracy.FULL, ENGINE);

    @Test
    void depthFiveIsFullyPopulatedNestedAndContainsTheQuery() {
        Instant q = BIRTH.plus(Duration.ofDays(1));
        RunningDasha r = timeline.running(q, 5);

        assertThat(r.depth()).isEqualTo(5);
        assertThat(r.lord(DashaLevel.MAHADASHA)).isEqualTo(Graha.VENUS);
        for (DashaLevel level : DashaLevel.values()) {
            DashaPeriod p = r.period(level);
            assertThat(p.contains(q)).as("%s contains query", level).isTrue();
            if (level != DashaLevel.MAHADASHA) {
                DashaPeriod parent = r.period(DashaLevel.ofRank(level.rank() - 1));
                assertThat(p.start()).isAfterOrEqualTo(parent.start());
                assertThat(p.end()).isBeforeOrEqualTo(parent.end());
            }
        }
    }

    @Test
    void depthThreeReturnsExactlyThreeLevels() {
        RunningDasha r = timeline.running(BIRTH.plus(Duration.ofDays(1)), 3);
        assertThat(r.stack()).extracting(DashaPeriod::level)
                .containsExactly(DashaLevel.MAHADASHA, DashaLevel.ANTARDASHA, DashaLevel.PRATYANTARDASHA);
    }

    @Test
    void aQueryInTheSecondCycleResolves() {
        Instant q = BIRTH.plus(Duration.ofSeconds(130L * YEAR_SECONDS));
        RunningDasha r = timeline.running(q, 5);
        assertThat(r.depth()).isEqualTo(5);
        assertThat(r.period(DashaLevel.MAHADASHA).contains(q)).isTrue();
    }

    @Test
    void rejectsQueryBeforeBirthAndBadDepth() {
        assertThatThrownBy(() -> timeline.running(BIRTH.minusSeconds(1), 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> timeline.running(BIRTH.plusSeconds(1), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> timeline.running(BIRTH.plusSeconds(1), 6))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void queryAtBirthInstantReturnsTheBirthBalanceMaha() {
        RunningDasha r = timeline.running(BIRTH, 5);
        assertThat(r.lord(DashaLevel.MAHADASHA)).isEqualTo(Graha.VENUS);
        assertThat(r.period(DashaLevel.MAHADASHA).start()).isEqualTo(timeline.balanceAtBirth().mahaStart());
    }
}
