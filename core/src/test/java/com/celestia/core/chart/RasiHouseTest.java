package com.celestia.core.chart;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.lordage.Sign;
import org.junit.jupiter.api.Test;

class RasiHouseTest {

    @Test
    void countsWholeSignsFromTheAscendantSign() {
        assertThat(Bhavas.rasiHouseOf(Sign.CAPRICORN, Sign.CAPRICORN)).isEqualTo(1);
        assertThat(Bhavas.rasiHouseOf(Sign.AQUARIUS, Sign.CAPRICORN)).isEqualTo(2);
        assertThat(Bhavas.rasiHouseOf(Sign.PISCES, Sign.CAPRICORN)).isEqualTo(3);
        assertThat(Bhavas.rasiHouseOf(Sign.SAGITTARIUS, Sign.CAPRICORN)).isEqualTo(12);
        assertThat(Bhavas.rasiHouseOf(Sign.SCORPIO, Sign.CAPRICORN)).isEqualTo(11);
    }

    @Test
    void everyAscendantSignYieldsAFullPermutationOfHouses() {
        for (Sign asc : Sign.values()) {
            var seen = new java.util.HashSet<Integer>();
            for (Sign g : Sign.values()) {
                int h = Bhavas.rasiHouseOf(g, asc);
                assertThat(h).isBetween(1, 12);
                seen.add(h);
            }
            assertThat(seen).hasSize(12);
        }
    }
}
