package com.celestia.core.chart;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.ephemeris.Angle;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.HouseResult;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

@EnabledIf("hasEphemerisData")
class CuspsTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    private final HouseResult houses = new SwissEphemerisHouseProvider()
            .houses(new BirthData(Instant.parse("1961-08-05T05:24:00Z"), 21.30694, -157.85833));

    @Test
    void twelveCuspsInHouseOrderWithLordChains() {
        List<Cusp> cusps = Cusps.fromHouseResult(houses);
        assertThat(cusps).hasSize(12);
        for (int i = 0; i < 12; i++) {
            Cusp c = cusps.get(i);
            assertThat(c.house()).isEqualTo(i + 1);
            assertThat(c.longitude()).isEqualTo(houses.cusp(i + 1));
            assertThat(c.subLord()).isEqualTo(c.lordChain().subLord());
            assertThat(c.lordChain().longitude()).isEqualTo(c.longitude());
        }
    }

    @Test
    void ascendantAnglePointMatchesCuspOne() {
        AnglePoint asc = Cusps.anglePoint(houses, Angle.ASCENDANT);
        assertThat(asc.angle()).isEqualTo(Angle.ASCENDANT);
        assertThat(asc.longitude()).isEqualTo(houses.cuspLongitudes().get(0));
        assertThat(asc.lordChain().subLord())
                .isEqualTo(Cusps.fromHouseResult(houses).get(0).subLord());
    }
}
