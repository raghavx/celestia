package com.celestia.ephemeris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.ephemeris.swisseph.SwissEphemerisSunriseProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Encodes contracts/sunrise-provider-api.md. */
class SunriseProviderContractTest {

    private final SunriseProvider provider = new SwissEphemerisSunriseProvider();

    // New Delhi
    private static final double LAT = 28.6139;
    private static final double LON = 77.209;

    @Test
    void returnsTheSunriseThatBeganTheKpDay() {
        Instant t = Instant.parse("2026-01-01T12:00:00Z");
        Optional<Instant> sunrise = provider.sunriseBefore(t, LAT, LON);
        assertThat(sunrise).isPresent();
        assertThat(sunrise.get()).isBeforeOrEqualTo(t);
        assertThat(sunrise.get()).isAfter(t.minus(Duration.ofHours(26)));
    }

    @Test
    void twoInstantsOnTheSameKpDayShareASunrise() {
        Instant morning = Instant.parse("2026-01-01T06:00:00Z");
        Instant evening = Instant.parse("2026-01-01T20:00:00Z");
        assertThat(provider.sunriseBefore(morning, LAT, LON))
                .isEqualTo(provider.sunriseBefore(evening, LAT, LON));
    }

    @Test
    void consecutiveKpDaysHaveDifferentSunrises() {
        Instant day1 = Instant.parse("2026-01-01T12:00:00Z");
        Instant day2 = Instant.parse("2026-01-02T12:00:00Z");
        Optional<Instant> s1 = provider.sunriseBefore(day1, LAT, LON);
        Optional<Instant> s2 = provider.sunriseBefore(day2, LAT, LON);
        assertThat(s1).isPresent();
        assertThat(s2).isPresent();
        long gapHours = Duration.between(s1.get(), s2.get()).toHours();
        assertThat(gapHours).isBetween(23L, 25L);
    }

    @Test
    void polarSummerHasNoSunrise() {
        // 80°N at the June solstice — the Sun is up around the clock.
        Instant t = Instant.parse("2021-06-21T12:00:00Z");
        assertThat(provider.sunriseBefore(t, 80.0, 20.0)).isEmpty();
    }

    @Test
    void isDeterministic() {
        Instant t = Instant.parse("2000-07-04T09:30:00Z");
        assertThat(provider.sunriseBefore(t, LAT, LON))
                .isEqualTo(provider.sunriseBefore(t, LAT, LON));
    }

    @Test
    void rejectsNullInstant() {
        assertThatThrownBy(() -> provider.sunriseBefore(null, LAT, LON))
                .isInstanceOf(EphemerisException.class);
    }

    @Test
    void rejectsOutOfRangeCoordinates() {
        Instant t = Instant.parse("2026-01-01T12:00:00Z");
        assertThatThrownBy(() -> provider.sunriseBefore(t, 91.0, LON))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.sunriseBefore(t, LAT, 181.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
