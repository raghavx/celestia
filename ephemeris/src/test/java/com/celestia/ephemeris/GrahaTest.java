package com.celestia.ephemeris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class GrahaTest {

    @Test
    void dashaYearsSumToOneHundredTwenty() {
        int total = Stream.of(Graha.values()).mapToInt(Graha::years).sum();
        assertThat(total).isEqualTo(Graha.CYCLE_YEARS);
    }

    @Test
    void vimshottariOrderStartsAtKetuAndHasNineGrahas() {
        assertThat(Graha.vimshottariOrder())
                .hasSize(9)
                .startsWith(Graha.KETU)
                .endsWith(Graha.MERCURY);
    }

    @Test
    void nextWrapsFromMercuryBackToKetu() {
        assertThat(Graha.MERCURY.next()).isEqualTo(Graha.KETU);
        assertThat(Graha.KETU.next()).isEqualTo(Graha.VENUS);
    }
}
