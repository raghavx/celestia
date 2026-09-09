package com.celestia.ephemeris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisHoraryHouseProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Encodes contracts/horary-houses-api.md. */
class HoraryHouseProviderContractTest {

    private final HoraryHouseProvider horary = new SwissEphemerisHoraryHouseProvider();
    private final SwissEphemerisHouseProvider natal = new SwissEphemerisHouseProvider();

    private static final BirthData DELHI =
            new BirthData(Instant.parse("2026-01-01T12:00:00Z"), 28.6139, 77.209);

    @Test
    void cuspOneIsBitIdenticalToTheGivenAscendant() {
        HouseResult r = horary.housesFor(DELHI, 144.944444);
        assertThat(r.cusp(1)).isEqualTo(144.944444);
        assertThat(r.angles().get(Angle.ASCENDANT)).isEqualTo(144.944444);
        assertThat(r.houseSystem()).isEqualTo(HouseSystem.PLACIDUS);
    }

    @Test
    void seedingANormalCastsAscendantReproducesItsTwelveCusps() {
        // several latitudes / instants
        BirthData[] cases = {
            new BirthData(Instant.parse("2026-01-01T12:00:00Z"), 28.6139, 77.209),
            new BirthData(Instant.parse("1961-08-05T05:24:00Z"), 21.30694, -157.85833),
            new BirthData(Instant.parse("1990-06-21T00:00:00Z"), -33.87, 151.21),
            new BirthData(Instant.parse("2000-03-20T18:00:00Z"), 51.5, -0.12),
        };
        for (BirthData bd : cases) {
            HouseResult reference = natal.houses(bd);
            HouseResult roundTrip = horary.housesFor(bd, reference.angles().get(Angle.ASCENDANT));
            for (int h = 1; h <= 12; h++) {
                double diffArcmin = Math.abs(
                        ((roundTrip.cusp(h) - reference.cusp(h) + 540.0) % 360.0 - 180.0)) * 60.0;
                assertThat(diffArcmin)
                        .as("lat %s cusp %d", bd.latitude(), h)
                        .isLessThan(1.0);
            }
        }
    }

    @Test
    void polarLatitudeIsRejected() {
        BirthData polar = new BirthData(Instant.parse("2026-01-01T12:00:00Z"), 70.0, 15.0);
        assertThatThrownBy(() -> horary.housesFor(polar, 100.0))
                .isInstanceOf(PlacidusUndefinedException.class);
    }

    @Test
    void outOfRangeInstantStillYieldsAResultFlaggedReduced() {
        BirthData ancient = new BirthData(Instant.parse("1600-03-21T06:00:00Z"), 28.6139, 77.209);
        HouseResult[] out = new HouseResult[1];
        assertThatCode(() -> out[0] = horary.housesFor(ancient, 100.0)).doesNotThrowAnyException();
        assertThat(out[0].accuracy()).isEqualTo(Accuracy.REDUCED);
        assertThat(out[0].cusp(1)).isEqualTo(100.0);
    }

    @Test
    void isDeterministic() {
        assertThat(horary.housesFor(DELHI, 100.0)).isEqualTo(horary.housesFor(DELHI, 100.0));
    }

    @Test
    void rejectsNullJudgment() {
        assertThatThrownBy(() -> horary.housesFor(null, 100.0))
                .isInstanceOf(EphemerisException.class);
    }
}
