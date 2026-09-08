package com.celestia.ephemeris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** US5: high-latitude births are refused; the Ascendant/MC stay available. */
class PlacidusPolarTest {

    private static final Instant T = Instant.parse("1990-06-21T12:00:00Z");
    private final HouseProvider provider = new SwissEphemerisHouseProvider();

    @Test
    void houseAtOrBeyondSixtySixIsRefusedWithANamedException() {
        assertThatThrownBy(() -> provider.houses(new BirthData(T, 70.0, 20.0)))
                .isInstanceOf(PlacidusUndefinedException.class)
                .hasMessageContaining("70.0");
        assertThatThrownBy(() -> provider.houses(new BirthData(T, -66.0, 20.0)))
                .isInstanceOf(PlacidusUndefinedException.class);
    }

    @Test
    void houseJustBelowTheLimitSucceeds() {
        assertThatCode(() -> provider.houses(new BirthData(T, 65.0, 20.0))).doesNotThrowAnyException();
    }

    @Test
    void latitudeBeyondNinetyIsPlainIllegalArgument() {
        assertThatThrownBy(() -> new BirthData(T, 91.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anglesOnlyWorksWellInsideThePolarCircle() {
        var angles = provider.anglesOnly(new BirthData(T, 78.0, 15.0));
        assertThat(angles).containsOnlyKeys(Angle.ASCENDANT, Angle.MIDHEAVEN);
        assertThat(angles.get(Angle.ASCENDANT)).isBetween(0.0, 360.0);
        assertThat(angles.get(Angle.MIDHEAVEN)).isBetween(0.0, 360.0);
    }

    @Test
    void configurablePolarLimit() {
        var strict = new SwissEphemerisConfig(
                SwissEphemerisConfig.resolve().ephePath(), 1800, 2100, 60.0);
        assertThatThrownBy(() -> new SwissEphemerisHouseProvider(strict).houses(new BirthData(T, 62.0, 0.0)))
                .isInstanceOf(PlacidusUndefinedException.class);
    }
}
