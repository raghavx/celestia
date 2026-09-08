package com.celestia.ephemeris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TimeScalesTest {

    @Test
    void convertsUtcInstantToJulianDay() {
        // 2000-01-01T12:00:00Z is J2000.0 == JD 2451545.0 (UT)
        JulianDay jd = TimeScales.of(Instant.parse("2000-01-01T12:00:00Z"));
        assertThat(jd.jdUt()).isCloseTo(2451545.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void terrestrialTimeIsAheadOfUniversalTimeByDeltaT() {
        JulianDay jd = TimeScales.of(Instant.parse("1961-08-05T05:24:00Z"));
        assertThat(jd.jdTt()).isGreaterThan(jd.jdUt());
        assertThat(jd.deltaTSeconds()).isBetween(25.0, 45.0); // ~33.8 s in 1961
        // jdTt is stored as jdUt + deltaT; recovering deltaT by subtraction loses
        // ~1e-5 s to the magnitude of jdUt (~2.44e6). deltaT is only known to ~0.1 s.
        assertThat((jd.jdTt() - jd.jdUt()) * 86_400.0)
                .isCloseTo(jd.deltaTSeconds(), org.assertj.core.data.Offset.offset(1e-3));
    }

    @Test
    void deltaTIsDeterministic() {
        Instant t = Instant.parse("1955-02-25T03:15:00Z");
        assertThat(TimeScales.of(t)).isEqualTo(TimeScales.of(t));
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> TimeScales.of(null)).isInstanceOf(EphemerisException.class);
    }
}
