package com.celestia.core.horary;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.judgement.RpSource;
import com.celestia.core.lordage.KpLordage;
import com.celestia.core.lordage.LordChain;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisSunriseProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** SPEC-005 US4: horary ruling planets use the number's Ascendant. */
@EnabledIf("hasEphemerisData")
class HoraryRulingPlanetsTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    private final SwissEphemerisPositionProvider positions = new SwissEphemerisPositionProvider();
    private final SwissEphemerisSunriseProvider sunrise = new SwissEphemerisSunriseProvider();
    private static final BirthData DELHI =
            new BirthData(Instant.parse("2026-01-01T12:00:00Z"), 28.6139, 77.209);

    @Test
    void lagnaLordsAreThoseOfTheNumbersAscendant() {
        var rp = HoraryRulingPlanets.at(100, DELHI, positions, sunrise);
        LordChain asc = KpLordage.chainFor(Horary249.arc(100).midpointDeg());

        assertThat(rp.sourcesFor(asc.signLord())).contains(RpSource.LAGNA_SIGN);
        assertThat(rp.sourcesFor(asc.starLord())).contains(RpSource.LAGNA_STAR);
        assertThat(rp.sourcesFor(asc.subLord())).contains(RpSource.LAGNA_SUB);
    }

    @Test
    void isDeterministic() {
        assertThat(HoraryRulingPlanets.at(100, DELHI, positions, sunrise))
                .isEqualTo(HoraryRulingPlanets.at(100, DELHI, positions, sunrise));
    }

    @Test
    void eachNumbersLagnaSignLordFollowsItsArc() {
        for (int n : new int[] {1, 60, 120, 180, 249}) {
            var rp = HoraryRulingPlanets.at(n, DELHI, positions, sunrise);
            var signLord = KpLordage.chainFor(Horary249.arc(n).midpointDeg()).signLord();
            assertThat(rp.sourcesFor(signLord))
                    .as("number %d lagna sign lord %s", n, signLord)
                    .contains(RpSource.LAGNA_SIGN);
        }
    }
}
