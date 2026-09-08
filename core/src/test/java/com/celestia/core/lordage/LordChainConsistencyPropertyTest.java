package com.celestia.core.lordage;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.dasha.Span;
import com.celestia.core.dasha.VimshottariPartition;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;

class LordChainConsistencyPropertyTest {

    @Property(tries = 5000)
    void subLordFromChainMatchesTheContainingPartitionSpan(
            @ForAll @DoubleRange(min = 0.0, max = 360.0, maxIncluded = false) double lambda) {
        LordChain chain = KpLordage.chainFor(lambda);

        Span containingSub = VimshottariPartition.subs(chain.nakshatra()).stream()
                .filter(s -> s.contains(lambda))
                .findFirst()
                .orElseThrow();
        assertThat(chain.subLord()).isEqualTo(containingSub.lord());

        Span containingSubSub = VimshottariPartition.subSubs(chain.nakshatra(), chain.subLord()).stream()
                .filter(s -> s.contains(lambda))
                .findFirst()
                .orElseThrow();
        assertThat(chain.subSubLord()).isEqualTo(containingSubSub.lord());
    }
}
