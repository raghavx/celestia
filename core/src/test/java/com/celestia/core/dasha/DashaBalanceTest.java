package com.celestia.core.dasha;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.Graha;
import java.time.Duration;
import java.time.Instant;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/** SPEC-004 US1: the balance of dasha at birth. */
class DashaBalanceTest {

    private static final Instant BIRTH = Instant.parse("2000-01-01T00:00:00Z");
    private static final EngineVersion ENGINE =
            new EngineVersion(EngineVersion.RULES, "test", "test", "swieph");
    private static final long YEAR_SECONDS = 365L * 86_400L + 21_600L; // 365.25 d

    private static DashaTimeline at(double moonLongitude) {
        return DashaTimeline.from(BIRTH, moonLongitude, Accuracy.FULL, ENGINE);
    }

    @Test
    void threeQuartersThroughAKetuNakshatra() {
        // Ashwini [0, 13°20'), lord Ketu (7 y); Moon at 10° is 0.75 of the way through
        DashaBalance b = at(10.0).balanceAtBirth();
        assertThat(b.mahaLord()).isEqualTo(Graha.KETU);
        assertThat(b.elapsedFraction()).isCloseTo(0.75, Offset.offset(1e-12));
        assertThat(b.elapsed()).isEqualTo(Duration.ofSeconds(scaled(0.75 * 7)));
        assertThat(b.balance()).isEqualTo(Duration.ofSeconds(scaled(0.25 * 7)));
    }

    @Test
    void halfwayThroughAVenusNakshatra() {
        // Bharani [13°20', 26°40'), lord Venus (20 y); Moon at 20° is halfway
        DashaBalance b = at(20.0).balanceAtBirth();
        assertThat(b.mahaLord()).isEqualTo(Graha.VENUS);
        assertThat(b.elapsedFraction()).isCloseTo(0.5, Offset.offset(1e-12));
        assertThat(b.elapsed()).isEqualTo(Duration.ofSeconds(10L * YEAR_SECONDS));
        assertThat(b.balance()).isEqualTo(Duration.ofSeconds(10L * YEAR_SECONDS));
    }

    @Test
    void exactlyAtANakshatraStartGivesTheFullBalance() {
        DashaBalance b = at(0.0).balanceAtBirth();
        assertThat(b.mahaLord()).isEqualTo(Graha.KETU);
        assertThat(b.elapsedFraction()).isEqualTo(0.0);
        assertThat(b.balance()).isEqualTo(Duration.ofSeconds(7L * YEAR_SECONDS));
        assertThat(b.elapsed()).isEqualTo(Duration.ZERO);
    }

    @Test
    void mahaSpanEqualsTheLordsFullLengthAndStartsBeforeBirth() {
        DashaBalance b = at(20.0).balanceAtBirth();
        assertThat(b.mahaStart()).isEqualTo(BIRTH.minus(b.elapsed()));
        assertThat(Duration.between(b.mahaStart(), b.mahaEnd()))
                .isCloseTo(Duration.ofSeconds(20L * YEAR_SECONDS), Duration.ofSeconds(1));
        assertThat(b.mahaEnd()).isEqualTo(BIRTH.plus(b.balance()));
    }

    private static long scaled(double years) {
        return Math.round(years * YEAR_SECONDS);
    }
}
