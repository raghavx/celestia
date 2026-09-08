package com.celestia.ephemeris;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

class AccuracyRangeTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @Test
    @EnabledIf("hasEphemerisData")
    void inRangeInstantIsFullAccuracy() {
        var result = new SwissEphemerisPositionProvider().positions(Instant.parse("1961-08-05T05:24:00Z"));
        assertThat(result.position(Graha.SUN).accuracy()).isEqualTo(Accuracy.FULL);
    }

    @Test
    void outOfRangeInstantReturnsReducedAccuracyNotAnError() {
        var result = new SwissEphemerisPositionProvider().positions(Instant.parse("1600-06-15T00:00:00Z"));
        assertThat(result.positions()).hasSize(9);
        for (GrahaPosition p : result.positions().values()) {
            assertThat(p.accuracy()).isEqualTo(Accuracy.REDUCED);
        }
        assertThat(result.engineVersion().ephemerisData()).isEqualTo("moseph");
    }

    @Test
    void withoutEphemerisDataEverythingIsReduced() {
        var noData = new SwissEphemerisConfig(null, 1800, 2100);
        var result = new SwissEphemerisPositionProvider(noData).positions(Instant.parse("1990-01-01T00:00:00Z"));
        for (GrahaPosition p : result.positions().values()) {
            assertThat(p.accuracy()).isEqualTo(Accuracy.REDUCED);
        }
    }
}
