package com.celestia.ephemeris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Instant;
import java.util.EnumSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Encodes contracts/ephemeris-api.md. */
class PositionProviderContractTest {

    private static PositionProvider provider;
    private static EphemerisResult result;

    @BeforeAll
    static void setUp() {
        provider = new SwissEphemerisPositionProvider();
        result = provider.positions(Instant.parse("1961-08-05T05:24:00Z"));
    }

    @Test
    void returnsExactlyTheNineGrahas() {
        assertThat(result.positions().keySet()).isEqualTo(EnumSet.allOf(Graha.class));
    }

    @Test
    void allLongitudesAreInHalfOpenCircle() {
        for (GrahaPosition p : result.positions().values()) {
            assertThat(p.longitude()).isGreaterThanOrEqualTo(0.0).isLessThan(360.0);
        }
    }

    @Test
    void ayanamsaIsKpNew() {
        assertThat(result.ayanamsa()).isEqualTo(Ayanamsa.KP_NEW);
    }

    @Test
    void ketuIsExactlyOppositeRahuAndBothRetrograde() {
        double rahu = result.position(Graha.RAHU).longitude();
        double ketu = result.position(Graha.KETU).longitude();
        assertThat((ketu - rahu + 360.0) % 360.0).isCloseTo(180.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(result.position(Graha.RAHU).retrograde()).isTrue();
        assertThat(result.position(Graha.KETU).retrograde()).isTrue();
    }

    @Test
    void retrogradeMatchesSpeedSignForEveryGraha() {
        for (GrahaPosition p : result.positions().values()) {
            assertThat(p.retrograde()).isEqualTo(p.speedPerDay() < 0.0);
        }
    }

    @Test
    void engineVersionIsPopulated() {
        EngineVersion v = result.engineVersion();
        assertThat(v.rules()).isEqualTo(EngineVersion.RULES);
        assertThat(v.sePort()).isNotBlank();
        assertThat(v.id()).contains("/");
    }

    @Test
    void isDeterministic() {
        Instant t = Instant.parse("1955-02-25T03:15:00Z");
        assertThat(provider.positions(t)).isEqualTo(provider.positions(t));
    }

    @Test
    void rejectsNullInstant() {
        assertThatThrownBy(() -> provider.positions(null)).isInstanceOf(EphemerisException.class);
    }
}
