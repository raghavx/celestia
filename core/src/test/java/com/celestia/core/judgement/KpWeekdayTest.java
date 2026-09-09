package com.celestia.core.judgement;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.ephemeris.SunriseProvider;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** SPEC-003 US4: the KP weekday runs sunrise-to-sunrise (SC-004, FR-014). */
class KpWeekdayTest {

    /** Returns the latest of a fixed 24 h sunrise grid at/-before the instant. */
    private static final class GridSunrise implements SunriseProvider {
        private final Instant base;

        GridSunrise(Instant base) {
            this.base = base;
        }

        @Override
        public Optional<Instant> sunriseBefore(Instant t, double lat, double lon) {
            long days = Math.floorDiv(Duration.between(base, t).getSeconds(), 86_400L);
            return Optional.of(base.plus(Duration.ofDays(days)));
        }
    }

    private static final SunriseProvider NO_SUNRISE = (t, lat, lon) -> Optional.empty();

    // A Thursday sunrise over New Delhi.
    private static final Instant SUNRISE = Instant.parse("2026-01-01T01:43:00Z");
    private static final double LAT = 28.6139;
    private static final double LON = 77.209;

    @Test
    void crossingTheSunriseBoundaryAdvancesTheWeekdayByOne() {
        SunriseProvider sunrise = new GridSunrise(SUNRISE);

        KpWeekday before = KpWeekday.resolve(
                SUNRISE.minus(Duration.ofMinutes(1)), LAT, LON, sunrise).weekday();
        KpWeekday after = KpWeekday.resolve(
                SUNRISE.plus(Duration.ofMinutes(1)), LAT, LON, sunrise).weekday();

        assertThat(after).isNotEqualTo(before);
        int diff = Math.floorMod(after.ordinal() - before.ordinal(), 7);
        assertThat(diff).isEqualTo(1);
    }

    @Test
    void weekdayIsThatOfTheSunriseLocalDate() {
        var resolution = KpWeekday.resolve(
                SUNRISE.plus(Duration.ofHours(6)), LAT, LON, new GridSunrise(SUNRISE));
        assertThat(resolution.fallback()).isFalse();
        assertThat(resolution.weekday()).isEqualTo(KpWeekday.THURSDAY);
        assertThat(resolution.weekday().lord().name()).isEqualTo("JUPITER");
    }

    @Test
    void noSunriseFallsBackToTheCivilWeekdayAndFlagsIt() {
        Instant t = Instant.parse("2021-06-21T12:00:00Z");
        var resolution = KpWeekday.resolve(t, 80.0, 20.0, NO_SUNRISE);

        assertThat(resolution.fallback()).isTrue();
        // civil weekday = LMT-adjusted date's day-of-week
        var expected = KpWeekday.of(
                t.plusSeconds(Math.round(20.0 / 15.0 * 3600.0))
                        .atOffset(ZoneOffset.UTC).toLocalDate().getDayOfWeek());
        assertThat(resolution.weekday()).isEqualTo(expected);
    }

    @Test
    void isDeterministic() {
        SunriseProvider sunrise = new GridSunrise(SUNRISE);
        Instant t = SUNRISE.plus(Duration.ofHours(10));
        assertThat(KpWeekday.resolve(t, LAT, LON, sunrise))
                .isEqualTo(KpWeekday.resolve(t, LAT, LON, sunrise));
    }
}
