package com.celestia.core.dasha;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.lordage.Nakshatra;
import com.celestia.ephemeris.Graha;
import java.util.List;
import org.apache.commons.numbers.fraction.BigFraction;
import org.junit.jupiter.api.Test;

class VimshottariPartitionTest {

    private static final BigFraction NAK_WIDTH = BigFraction.of(40, 3);

    @Test
    void subsOfAshwiniStartAtZeroWithKetuThenVenus() {
        List<Span> subs = VimshottariPartition.subs(Nakshatra.ASHWINI);

        assertThat(subs).hasSize(9);
        assertThat(subs.get(0).lord()).isEqualTo(Graha.KETU);
        assertThat(subs.get(0).start()).isEqualTo(BigFraction.ZERO);
        // Ketu: 7/120 * 13d20' = 0d46'40" = 7/9 deg
        assertThat(subs.get(0).width()).isEqualTo(BigFraction.of(7, 9));
        // Venus: 20/120 * 13d20' = 2d13'20" = 20/9 deg
        assertThat(subs.get(1).lord()).isEqualTo(Graha.VENUS);
        assertThat(subs.get(1).width()).isEqualTo(BigFraction.of(20, 9));
    }

    @Test
    void everyNakshatraSubPartitionSumsExactlyToNakshatraWidth() {
        for (Nakshatra n : Nakshatra.values()) {
            List<Span> subs = VimshottariPartition.subs(n);
            BigFraction total = BigFraction.ZERO;
            for (Span s : subs) {
                total = total.add(s.width());
            }
            assertThat(total).as("sub widths of %s", n).isEqualTo(NAK_WIDTH);
        }
    }

    @Test
    void subsAreContiguousAndOrderedFromStarLord() {
        for (Nakshatra n : Nakshatra.values()) {
            List<Span> subs = VimshottariPartition.subs(n);
            assertThat(subs.get(0).lord()).isEqualTo(n.lord());
            assertThat(subs.get(0).start().doubleValue()).isEqualTo(n.startLongitude());
            for (int i = 0; i < 8; i++) {
                assertThat(subs.get(i).end()).as("%s sub %d/%d boundary", n, i, i + 1).isEqualTo(subs.get(i + 1).start());
                assertThat(subs.get(i + 1).lord()).isEqualTo(subs.get(i).lord().next());
            }
        }
    }

    @Test
    void the243SubsTileTheWholeZodiacExactly() {
        BigFraction cursor = BigFraction.ZERO;
        int count = 0;
        for (Nakshatra n : Nakshatra.values()) {
            for (Span s : VimshottariPartition.subs(n)) {
                assertThat(s.start()).as("gap/overlap before %s sub of %s", s.lord(), n).isEqualTo(cursor);
                cursor = s.end();
                count++;
            }
        }
        assertThat(count).isEqualTo(243);
        assertThat(cursor).isEqualTo(BigFraction.of(360));
    }

    @Test
    void everySubSubPartitionSumsExactlyToItsSubWidth() {
        for (Nakshatra n : Nakshatra.values()) {
            for (Span sub : VimshottariPartition.subs(n)) {
                List<Span> subSubs = VimshottariPartition.subSubs(n, sub.lord());
                BigFraction total = BigFraction.ZERO;
                for (Span ss : subSubs) {
                    total = total.add(ss.width());
                }
                assertThat(total).as("sub-subs of %s / %s", n, sub.lord()).isEqualTo(sub.width());
                assertThat(subSubs.get(0).lord()).isEqualTo(sub.lord());
                assertThat(subSubs.get(0).start()).isEqualTo(sub.start());
            }
        }
    }
}
