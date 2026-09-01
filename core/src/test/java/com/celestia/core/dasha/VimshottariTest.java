package com.celestia.core.dasha;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class VimshottariTest {

    @Test
    void dashaYearsSumToOneHundredTwenty() {
        int total = Stream.of(Vimshottari.values())
                .mapToInt(Vimshottari::years)
                .sum();
        assertThat(total).isEqualTo(Vimshottari.CYCLE_YEARS);
    }

    @Test
    void sequenceStartsAtKetuAndHasNineLords() {
        assertThat(Vimshottari.sequence())
                .hasSize(9)
                .startsWith(Vimshottari.KETU)
                .endsWith(Vimshottari.MERCURY);
    }

    @Test
    void nextWrapsFromMercuryBackToKetu() {
        assertThat(Vimshottari.MERCURY.next()).isEqualTo(Vimshottari.KETU);
        assertThat(Vimshottari.KETU.next()).isEqualTo(Vimshottari.VENUS);
    }
}
