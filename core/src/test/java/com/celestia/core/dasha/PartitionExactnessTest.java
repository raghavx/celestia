package com.celestia.core.dasha;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.lordage.Nakshatra;
import java.util.List;
import org.apache.commons.numbers.fraction.BigFraction;
import org.junit.jupiter.api.Test;

/** SC-003: partition widths sum <b>exactly</b> to the parent width, no float residue. */
class PartitionExactnessTest {

    private static final BigFraction NAK_WIDTH = BigFraction.of(40, 3);

    @Test
    void allSubAndSubSubWidthsSumExactly() {
        for (Nakshatra n : Nakshatra.values()) {
            List<Span> subs = VimshottariPartition.subs(n);

            BigFraction subTotal = BigFraction.ZERO;
            for (Span sub : subs) {
                subTotal = subTotal.add(sub.width());

                BigFraction ssTotal = BigFraction.ZERO;
                List<Span> subSubs = VimshottariPartition.subSubs(n, sub.lord());
                for (Span ss : subSubs) {
                    ssTotal = ssTotal.add(ss.width());
                }
                assertThat(ssTotal).as("sub-subs of %s/%s", n, sub.lord()).isEqualTo(sub.width());

                // sub-subs are contiguous and start at the sub start
                assertThat(subSubs.get(0).start()).isEqualTo(sub.start());
                assertThat(subSubs.get(subSubs.size() - 1).end()).isEqualTo(sub.end());
            }
            assertThat(subTotal).as("subs of %s", n).isEqualTo(NAK_WIDTH);
        }
    }

    @Test
    void the243SubDivisionsAreExposedInZodiacOrder() {
        List<Span> all = VimshottariPartition.subDivisions();
        assertThat(all).hasSize(243);
        BigFraction cursor = BigFraction.ZERO;
        for (Span s : all) {
            assertThat(s.start()).isEqualTo(cursor);
            cursor = s.end();
        }
        assertThat(cursor).isEqualTo(BigFraction.of(360));
    }
}
