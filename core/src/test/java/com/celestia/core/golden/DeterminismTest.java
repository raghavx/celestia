package com.celestia.core.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.lordage.KpLordage;
import com.celestia.ephemeris.EphemerisResult;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.PositionProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** SC-005: identical input yields identical output. */
class DeterminismTest {

    @Test
    void positionsAreIdenticalAcrossRepeatedCalls() {
        PositionProvider provider = new SwissEphemerisPositionProvider();
        Instant t = GoldenChart.load("obama-1961").instant();

        EphemerisResult a = provider.positions(t);
        EphemerisResult b = provider.positions(t);
        assertThat(a).isEqualTo(b);

        // a fresh provider instance must agree too (no hidden mutable state)
        EphemerisResult c = new SwissEphemerisPositionProvider().positions(t);
        assertThat(c.positions()).isEqualTo(a.positions());
        assertThat(c.engineVersion()).isEqualTo(a.engineVersion());
    }

    @Test
    void lordChainIsPurelyDeterministic() {
        for (double d = 0.0; d < 360.0; d += 0.37) {
            assertThat(KpLordage.chainFor(d)).isEqualTo(KpLordage.chainFor(d));
        }
    }

    @Test
    void ketuLongitudeIsExactlyRahuPlus180AcrossRuns() {
        PositionProvider provider = new SwissEphemerisPositionProvider();
        for (GoldenChart chart : GoldenChart.loadAll()) {
            EphemerisResult r = provider.positions(chart.instant());
            double rahu = r.position(Graha.RAHU).longitude();
            double ketu = r.position(Graha.KETU).longitude();
            assertThat((ketu - rahu + 360.0) % 360.0).isCloseTo(180.0, org.assertj.core.data.Offset.offset(1e-9));
        }
    }
}
