package com.celestia.ephemeris;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

class HouseAccuracyTest {

    private static final double LAT = 21.30694;
    private static final double LON = -157.85833;

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @Test
    @EnabledIf("hasEphemerisData")
    void inRangeBirthIsFullAccuracy() {
        var r = new SwissEphemerisHouseProvider()
                .houses(new BirthData(Instant.parse("1961-08-05T05:24:00Z"), LAT, LON));
        assertThat(r.accuracy()).isEqualTo(Accuracy.FULL);
    }

    @Test
    void outOfRangeBirthReturnsReducedNotAnError() {
        var r = new SwissEphemerisHouseProvider()
                .houses(new BirthData(Instant.parse("1600-06-15T00:00:00Z"), LAT, LON));
        assertThat(r.accuracy()).isEqualTo(Accuracy.REDUCED);
        assertThat(r.cuspLongitudes()).hasSize(12);
        assertThat(r.engineVersion().ephemerisData()).isEqualTo("moseph");
    }
}
