package com.celestia.core.dasha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.Graha;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-004 US3: windowed period enumeration. */
class DashaWindowTest {

    private static final Instant BIRTH = Instant.parse("2000-01-01T00:00:00Z");
    private static final EngineVersion ENGINE =
            new EngineVersion(EngineVersion.RULES, "test", "test", "swieph");

    // Moon 20° -> Venus Mahadasha, mahaStart = BIRTH - 10 y, mahaEnd = BIRTH + 10 y
    private final DashaTimeline timeline = DashaTimeline.from(BIRTH, 20.0, Accuracy.FULL, ENGINE);

    @Test
    void enumeratesTheAntardashasOverlappingAWindow() {
        // mahaStart + [13 y, 17 y]  ->  Saturn [12.833, 16.0) and Mercury [16.0, 18.833)
        Instant from = BIRTH.plus(Duration.ofDays(3 * 365));
        Instant to = BIRTH.plus(Duration.ofDays(7 * 365));
        List<DashaPeriod> antars = timeline.periods(DashaLevel.ANTARDASHA, from, to);

        assertThat(antars).extracting(p -> p.lord()).containsExactly(Graha.SATURN, Graha.MERCURY);
        assertThat(antars.get(0).start()).isBefore(from);           // in progress at `from`
        assertThat(antars.get(0).end()).isEqualTo(antars.get(1).start());  // contiguous
        DashaBalance b = timeline.balanceAtBirth();
        for (DashaPeriod p : antars) {
            assertThat(p.parentLords()).containsExactly(Graha.VENUS);
            assertThat(p.start()).isAfterOrEqualTo(b.mahaStart());
            assertThat(p.end()).isBeforeOrEqualTo(b.mahaEnd());
        }
    }

    @Test
    void rejectsAnInvalidWindow() {
        assertThatThrownBy(() -> timeline.periods(
                DashaLevel.ANTARDASHA, BIRTH.plusSeconds(10), BIRTH.plusSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> timeline.periods(
                DashaLevel.ANTARDASHA, BIRTH.minusSeconds(1), BIRTH.plusSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAWindowMisScaledForTheLevel() {
        // Pranas over 20 years is millions of periods -> the safety cap fires
        assertThatThrownBy(() -> timeline.periods(
                DashaLevel.PRANA, BIRTH, BIRTH.plus(Duration.ofDays(20 * 365))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PRANA");
    }

    @Test
    void aSingleShortWindowReturnsTheOnePeriodInProgress() {
        Instant from = BIRTH.plus(Duration.ofDays(1));
        List<DashaPeriod> pranas =
                timeline.periods(DashaLevel.PRANA, from, from.plusMillis(1));
        assertThat(pranas).hasSize(1);
        assertThat(pranas.get(0).contains(from)).isTrue();
    }
}
