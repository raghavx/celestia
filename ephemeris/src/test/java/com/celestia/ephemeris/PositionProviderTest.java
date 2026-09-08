package com.celestia.ephemeris;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PositionProviderTest {

    private final PositionProvider provider = new SwissEphemerisPositionProvider();

    @Test
    void mercuryIsFlaggedRetrogradeWhenMovingBackwards() {
        // Steve Jobs, 1955-02-25 03:15Z — Mercury is retrograde on this date.
        GrahaPosition mercury = provider.positions(Instant.parse("1955-02-25T03:15:00Z")).position(Graha.MERCURY);
        assertThat(mercury.speedPerDay()).isLessThan(0.0);
        assertThat(mercury.retrograde()).isTrue();
    }

    @Test
    void sunIsNeverRetrograde() {
        GrahaPosition sun = provider.positions(Instant.parse("1961-08-05T05:24:00Z")).position(Graha.SUN);
        assertThat(sun.speedPerDay()).isGreaterThan(0.0);
        assertThat(sun.retrograde()).isFalse();
    }

    @Test
    void ketuLatitudeIsOppositeRahu() {
        var r = provider.positions(Instant.parse("1961-08-05T05:24:00Z"));
        assertThat(r.position(Graha.KETU).latitude()).isEqualTo(-r.position(Graha.RAHU).latitude());
    }
}
