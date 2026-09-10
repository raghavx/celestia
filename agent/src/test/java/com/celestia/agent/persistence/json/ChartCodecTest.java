package com.celestia.agent.persistence.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.chart.NatalChartFactory;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EphemerisResult;
import com.celestia.ephemeris.HouseResult;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** T017 — no container. */
class ChartCodecTest {

    private final ChartCodec codec = new ChartCodec();

    private static final SwissEphemerisConfig CONFIG = SwissEphemerisConfig.resolve();
    private static final SwissEphemerisPositionProvider POSITIONS =
            new SwissEphemerisPositionProvider(CONFIG);
    private static final SwissEphemerisHouseProvider HOUSES = new SwissEphemerisHouseProvider(CONFIG);

    private static final BirthData BIRTH =
            new BirthData(Instant.parse("1985-06-15T09:00:00Z"), 18.5204, 73.8567);

    @Test
    void roundTripsAChartThroughJson() {
        EphemerisResult eph = POSITIONS.positions(BIRTH.instant());
        HouseResult houses = HOUSES.houses(BIRTH);
        NatalChart expected = NatalChartFactory.assemble(BIRTH, eph, houses);

        String json = codec.write(eph, houses);
        NatalChart reloaded = codec.read(json).orElseThrow();

        assertThat(reloaded.positions()).isEqualTo(expected.positions());
        assertThat(reloaded.cusps()).isEqualTo(expected.cusps());
        assertThat(reloaded.ascendant()).isEqualTo(expected.ascendant());
        assertThat(reloaded.engineVersion()).isEqualTo(expected.engineVersion());
        assertThat(reloaded.birthData()).isEqualTo(BIRTH);
    }

    @Test
    void aCorruptOrUnknownSchemaPayloadReadsAsEmpty() {
        assertThat(codec.read("not json at all")).isEmpty();
        assertThat(codec.read("{\"schema\":999,\"ephemeris\":{},\"houses\":{}}")).isEmpty();
        assertThat(codec.read("{\"schema\":1}")).isEmpty();
    }
}
