package com.celestia.core.dasha;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.dasha.VimshottariSplit.Portion;
import com.celestia.ephemeris.Graha;
import java.util.List;
import org.apache.commons.numbers.fraction.BigFraction;
import org.junit.jupiter.api.Test;

/** SPEC-004 SC-003: the nine-way weight split is exact. */
class VimshottariSplitTest {

    private static final List<BigFraction> TOTALS = List.of(
            BigFraction.of(120),                 // one cycle in years
            BigFraction.of(40, 3),               // one nakshatra in degrees
            BigFraction.of(365L * 86_400L + 21_600L).multiply(120), // one cycle in seconds
            BigFraction.of(7).multiply(BigFraction.of(365L * 86_400L + 21_600L)), // a Ketu Maha in seconds
            BigFraction.of(1, 7));               // an awkward rational

    @Test
    void everySplitSumsToTheTotalExactlyInVimshottariOrder() {
        for (Graha from : Graha.values()) {
            for (BigFraction total : TOTALS) {
                List<Portion> portions = VimshottariSplit.of(total, from);

                assertThat(portions).hasSize(9);
                Graha expected = from;
                BigFraction sum = BigFraction.ZERO;
                for (Portion p : portions) {
                    assertThat(p.lord()).isEqualTo(expected);
                    assertThat(p.span())
                            .isEqualTo(total.multiply(BigFraction.of(expected.years(), 120)));
                    assertThat(p.span().signum()).isPositive();
                    sum = sum.add(p.span());
                    expected = expected.next();
                }
                assertThat(sum).as("portions of %s from %s sum exactly", total, from).isEqualTo(total);
                assertThat(expected).as("cycles back to the first lord").isEqualTo(from);
            }
        }
    }
}
