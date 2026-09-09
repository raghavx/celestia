package com.celestia.core.dasha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.core.dasha.VimshottariSplit.Portion;
import com.celestia.ephemeris.Graha;
import java.time.Instant;
import java.util.List;
import org.apache.commons.numbers.fraction.BigFraction;
import org.junit.jupiter.api.Test;

class FoundationalTypesTest {

    private static final Instant T0 = Instant.parse("2000-01-01T00:00:00Z");

    @Test
    void dashaLevelRanksAndOfRank() {
        assertThat(DashaLevel.MAHADASHA.rank()).isEqualTo(1);
        assertThat(DashaLevel.PRANA.rank()).isEqualTo(5);
        for (DashaLevel l : DashaLevel.values()) {
            assertThat(DashaLevel.ofRank(l.rank())).isEqualTo(l);
        }
        assertThatThrownBy(() -> DashaLevel.ofRank(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DashaLevel.ofRank(6)).isInstanceOf(IllegalArgumentException.class);
        assertThat(DashaLevel.MAHADASHA.parent()).isEmpty();
        assertThat(DashaLevel.PRANA.child()).isEmpty();
        assertThat(DashaLevel.ANTARDASHA.parent()).contains(DashaLevel.MAHADASHA);
    }

    @Test
    void vimshottariSplitIsNinePortionsInOrderSummingExactly() {
        BigFraction total = BigFraction.of(120);
        List<Portion> portions = VimshottariSplit.of(total, Graha.MOON);

        assertThat(portions).hasSize(9);
        assertThat(portions.stream().map(Portion::lord).toList())
                .containsExactly(Graha.MOON, Graha.MARS, Graha.RAHU, Graha.JUPITER, Graha.SATURN,
                        Graha.MERCURY, Graha.KETU, Graha.VENUS, Graha.SUN);
        // total 120 -> each portion is exactly the lord's year weight
        for (Portion p : portions) {
            assertThat(p.span()).isEqualTo(BigFraction.of(p.lord().years()));
        }
        BigFraction sum = portions.stream().map(Portion::span)
                .reduce(BigFraction.ZERO, BigFraction::add);
        assertThat(sum).isEqualTo(total);
    }

    @Test
    void vimshottariSplitRejectsNonPositiveTotal() {
        assertThatThrownBy(() -> VimshottariSplit.of(BigFraction.ZERO, Graha.KETU))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VimshottariSplit.of(BigFraction.of(-1), Graha.KETU))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dashaPeriodRejectsBadSpanAndParentChain() {
        assertThatThrownBy(() -> new DashaPeriod(
                DashaLevel.MAHADASHA, Graha.SUN, T0, T0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        // ANTARDASHA needs exactly one parent lord
        assertThatThrownBy(() -> new DashaPeriod(
                DashaLevel.ANTARDASHA, Graha.SUN, T0, T0.plusSeconds(1), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new DashaPeriod(DashaLevel.MAHADASHA, Graha.SUN, T0, T0.plusSeconds(10), List.of())
                .contains(T0)).isTrue();
    }

    @Test
    void runningDashaRejectsANonNestedStack() {
        DashaPeriod maha = new DashaPeriod(
                DashaLevel.MAHADASHA, Graha.SUN, T0, T0.plusSeconds(1000), List.of());
        DashaPeriod antarOutside = new DashaPeriod(
                DashaLevel.ANTARDASHA, Graha.SUN, T0.plusSeconds(500), T0.plusSeconds(2000),
                List.of(Graha.SUN));
        assertThatThrownBy(() -> new RunningDasha(T0.plusSeconds(600), List.of(maha, antarOutside)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
