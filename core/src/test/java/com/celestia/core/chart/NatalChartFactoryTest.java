package com.celestia.core.chart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.core.golden.GoldenChart;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EphemerisResult;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.HouseResult;
import com.celestia.ephemeris.PlacidusUndefinedException;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

@EnabledIf("hasEphemerisData")
class NatalChartFactoryTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    private final NatalChartFactory factory = new NatalChartFactory(
            new SwissEphemerisPositionProvider(), new SwissEphemerisHouseProvider());

    private final BirthData obama =
            new BirthData(Instant.parse("1961-08-05T05:24:00Z"), 21.30694, -157.85833);

    @Test
    void assembleIsDeterministic() {
        EphemerisResult pos = new SwissEphemerisPositionProvider().positions(obama.instant());
        HouseResult houses = new SwissEphemerisHouseProvider().houses(obama);
        assertThat(NatalChartFactory.assemble(obama, pos, houses))
                .isEqualTo(NatalChartFactory.assemble(obama, pos, houses));
    }

    @Test
    void castIsDeterministic() {
        assertThat(factory.cast(obama)).isEqualTo(factory.cast(obama));
    }

    @Test
    void chartExposesEveryPieceConsistently() {
        NatalChart c = factory.cast(obama);
        assertThat(c.cusps()).hasSize(12);
        assertThat(c.cusp(1).longitude()).isEqualTo(c.ascendant().longitude());
        for (int h = 1; h <= 12; h++) {
            assertThat(c.cuspSubLord(h)).isEqualTo(c.cusp(h).lordChain().subLord());
        }
        assertThat(c.placements().keySet()).containsExactlyInAnyOrder(Graha.values());
        for (Graha g : Graha.values()) {
            assertThat(c.placement(g).bhava()).isBetween(1, 12);
            assertThat(c.placement(g).rasiHouse()).isBetween(1, 12);
            // bhava is consistent with Bhavas over the cusp ring
            assertThat(c.placement(g).bhava())
                    .isEqualTo(Bhavas.bhavaOf(c.position(g).longitude(),
                            c.cusps().stream().map(Cusp::longitude).toList()));
        }
    }

    @Test
    void castPropagatesPolarRejection() {
        BirthData polar = new BirthData(Instant.parse("1990-01-01T00:00:00Z"), 70.0, 15.0);
        assertThatThrownBy(() -> factory.cast(polar)).isInstanceOf(PlacidusUndefinedException.class);
    }

    @Test
    void goldenChartsCastWithoutInvariantErrors() {
        for (GoldenChart gc : GoldenChart.loadAll()) {
            NatalChart c = factory.cast(new BirthData(gc.instant(), gc.latitude(), gc.longitude()));
            assertThat(c.engineVersion().id()).contains(com.celestia.ephemeris.EngineVersion.RULES);
        }
    }
}
