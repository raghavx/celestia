package com.celestia.ephemeris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Encodes contracts/house-provider-api.md. */
class HouseProviderContractTest {

    // Obama, Honolulu
    private static final BirthData OBAMA =
            new BirthData(Instant.parse("1961-08-05T05:24:00Z"), 21.30694, -157.85833);

    private static HouseProvider provider;
    private static HouseResult result;

    @BeforeAll
    static void setUp() {
        provider = new SwissEphemerisHouseProvider();
        result = provider.houses(OBAMA);
    }

    @Test
    void twelveFiniteCuspsInHalfOpenCircle() {
        assertThat(result.cuspLongitudes()).hasSize(12);
        for (double c : result.cuspLongitudes()) {
            assertThat(c).isGreaterThanOrEqualTo(0.0).isLessThan(360.0);
        }
    }

    @Test
    void cuspOneIsBitIdenticalToTheAscendant() {
        assertThat(Double.compare(result.cuspLongitudes().get(0), result.angles().get(Angle.ASCENDANT)))
                .isZero();
        assertThat(result.cusp(1)).isEqualTo(result.angles().get(Angle.ASCENDANT));
    }

    @Test
    void cuspRingIsMonotoneWithOneWrap() {
        var c = result.cuspLongitudes();
        double wraps = 0;
        for (int i = 0; i < 12; i++) {
            double next = c.get((i + 1) % 12);
            double arc = (next - c.get(i)) % 360.0;
            if (arc < 0) arc += 360.0;
            assertThat(arc).as("forward arc house %d", i + 1).isGreaterThan(0.0);
            if (next < c.get(i)) wraps++;
        }
        assertThat(wraps).isEqualTo(1.0);
    }

    @Test
    void houseSystemIsPlacidusAndVersionPopulated() {
        assertThat(result.houseSystem()).isEqualTo(HouseSystem.PLACIDUS);
        assertThat(result.engineVersion().rules()).isEqualTo(EngineVersion.RULES);
        assertThat(result.engineVersion().sePort()).isNotBlank();
    }

    @Test
    void isDeterministic() {
        assertThat(provider.houses(OBAMA)).isEqualTo(provider.houses(OBAMA));
        assertThat(new SwissEphemerisHouseProvider().houses(OBAMA).cuspLongitudes())
                .isEqualTo(result.cuspLongitudes());
    }

    @Test
    void anglesOnlyReturnsAscAndMcAtAnyLatitude() {
        var polar = provider.anglesOnly(new BirthData(OBAMA.instant(), 78.0, 15.0));
        assertThat(polar).containsOnlyKeys(Angle.ASCENDANT, Angle.MIDHEAVEN);
        assertThat(polar.get(Angle.ASCENDANT)).isBetween(0.0, 360.0);
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> provider.houses(null)).isInstanceOf(EphemerisException.class);
        assertThatThrownBy(() -> new BirthData(OBAMA.instant(), 91.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BirthData(OBAMA.instant(), 0.0, 181.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
