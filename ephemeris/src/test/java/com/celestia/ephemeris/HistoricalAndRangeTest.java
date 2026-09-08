package com.celestia.ephemeris;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** US5: results across 1800-2100, and a flagged result outside it. */
class HistoricalAndRangeTest {

    private final PositionProvider provider = new SwissEphemerisPositionProvider();

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @Test
    @EnabledIf("hasEphemerisData")
    void historicalDatesInRangeAreFullAccuracyWithSaneLongitudes() {
        for (String iso : List.of("1850-03-21T00:00:00Z", "1950-06-21T12:00:00Z", "2050-09-23T06:00:00Z")) {
            EphemerisResult r = provider.positions(Instant.parse(iso));
            for (GrahaPosition p : r.positions().values()) {
                assertThat(p.accuracy()).as("%s / %s", iso, p.graha()).isEqualTo(Accuracy.FULL);
                assertThat(p.longitude()).isBetween(0.0, 360.0);
                assertThat(Double.isFinite(p.latitude())).isTrue();
                assertThat(Double.isFinite(p.speedPerDay())).isTrue();
            }
        }
    }

    @Test
    void datesOutsideRangeReturnReducedResultNotAnError() {
        for (String iso : List.of("1600-01-01T00:00:00Z", "2200-12-31T23:00:00Z")) {
            EphemerisResult r = provider.positions(Instant.parse(iso));
            assertThat(r.positions()).hasSize(9);
            for (GrahaPosition p : r.positions().values()) {
                assertThat(p.accuracy()).as("%s / %s", iso, p.graha()).isEqualTo(Accuracy.REDUCED);
            }
        }
    }

    @Test
    void deltaTIsConsistentAndMatchesTheStoredSeconds() {
        Instant t = Instant.parse("1955-02-25T03:15:00Z");
        JulianDay a = TimeScales.of(t);
        JulianDay b = TimeScales.of(t);
        assertThat(a).isEqualTo(b);
        assertThat((a.jdTt() - a.jdUt()) * 86_400.0)
                .isCloseTo(a.deltaTSeconds(), org.assertj.core.data.Offset.offset(1e-3));
        assertThat(a.deltaTSeconds()).isPositive();
    }
}
