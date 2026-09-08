package com.celestia.core.dasha;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.lordage.Nakshatra;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;

/** SC-004: the sub partition tiles the whole zodiac with no gap or overlap. */
class ZodiacTilingPropertyTest {

    @Property(tries = 20000)
    void everyLongitudeLandsInExactlyOneSubAndOneSubSub(
            @ForAll @DoubleRange(min = 0.0, max = 360.0, maxIncluded = false) double lambda) {
        Nakshatra nak = Nakshatra.at(lambda);

        List<Span> subs = VimshottariPartition.subs(nak);
        long subHits = subs.stream().filter(s -> s.contains(lambda)).count();
        assertThat(subHits).as("subs containing %s", lambda).isEqualTo(1L);

        Span sub = subs.stream().filter(s -> s.contains(lambda)).findFirst().orElseThrow();
        long subSubHits = VimshottariPartition.subSubs(nak, sub.lord()).stream()
                .filter(s -> s.contains(lambda))
                .count();
        assertThat(subSubHits).as("sub-subs containing %s", lambda).isEqualTo(1L);
    }
}
