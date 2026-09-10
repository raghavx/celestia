package com.celestia.core.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.dasha.DashaLevel;
import com.celestia.core.dasha.DashaPeriod;
import com.celestia.core.dasha.RunningDasha;
import com.celestia.core.lordage.KpLordage;
import com.celestia.ephemeris.Graha;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** SPEC-006 US4: the four verdicts + traceability. */
class VerdictRuleTest {

    private static final Instant T = Instant.parse("2020-01-01T00:00:00Z");

    private static DashaSignificators dashaWith(Map<Integer, Set<Graha>> activated) {
        RunningDasha running = new RunningDasha(T, List.of(new DashaPeriod(
                DashaLevel.MAHADASHA, Graha.SUN, T.minusSeconds(1), T.plusSeconds(1), List.of())));
        List<ActivatedHouse> houses = new ArrayList<>();
        activated.forEach((h, lords) -> houses.add(new ActivatedHouse(h, lords.size(), lords)));
        houses.sort((a, b) -> Integer.compare(a.house(), b.house()));
        return new DashaSignificators(running, Map.of(), houses, false);
    }

    private static TransitContribution transitSupporting(Set<Integer> moon, Set<Integer> sun) {
        return new TransitContribution(
                KpLordage.chainFor(0.0), KpLordage.chainFor(0.0), moon, sun, false);
    }

    @Test
    void favourableWhenActivatedAndTriggeredAndNotOutweighed() {
        var dasha = dashaWith(Map.of(
                2, Set.of(Graha.VENUS), 7, Set.of(Graha.VENUS), 11, Set.of(Graha.JUPITER)));
        var transit = transitSupporting(Set.of(7), Set.of());
        MatterVerdict mv = VerdictRule.evaluate(Matter.MARRIAGE, dasha, transit);

        assertThat(mv.verdict()).isEqualTo(Verdict.FAVOURABLE);
        assertThat(mv.favourableHit()).containsExactlyInAnyOrder(2, 7, 11);
        assertThat(mv.obstructiveHit()).isEmpty();
        assertThat(mv.transits()).containsExactly(TransitBody.MOON);
        assertThat(mv.lords()).contains(Graha.VENUS, Graha.JUPITER);
    }

    @Test
    void mixedWhenRipeButNoTransitTriggersIt() {
        var dasha = dashaWith(Map.of(2, Set.of(Graha.VENUS), 7, Set.of(Graha.VENUS)));
        var transit = transitSupporting(Set.of(), Set.of());
        MatterVerdict mv = VerdictRule.evaluate(Matter.MARRIAGE, dasha, transit);

        assertThat(mv.verdict()).isEqualTo(Verdict.MIXED);
        assertThat(mv.favourableHit()).containsExactlyInAnyOrder(2, 7);
        assertThat(mv.transits()).isEmpty();
    }

    @Test
    void unfavourableWhenTheObstructiveSideOutweighs() {
        var dasha = dashaWith(Map.of(
                2, Set.of(Graha.VENUS),                                   // fav strength 1
                1, Set.of(Graha.SUN, Graha.MOON, Graha.MARS),             // obs strength 3
                6, Set.of(Graha.SUN, Graha.MOON, Graha.MARS)));           // obs strength 3
        var transit = transitSupporting(Set.of(2), Set.of());
        MatterVerdict mv = VerdictRule.evaluate(Matter.MARRIAGE, dasha, transit);

        assertThat(mv.verdict()).isEqualTo(Verdict.UNFAVOURABLE);
        assertThat(mv.favourableHit()).containsExactly(2);
        assertThat(mv.obstructiveHit()).containsExactlyInAnyOrder(1, 6);
    }

    @Test
    void quietWhenNeitherSideIsActivated() {
        var dasha = dashaWith(Map.of(5, Set.of(Graha.SATURN))); // 5 is not in MARRIAGE's groups
        var transit = transitSupporting(Set.of(5), Set.of());
        MatterVerdict mv = VerdictRule.evaluate(Matter.MARRIAGE, dasha, transit);

        assertThat(mv.verdict()).isEqualTo(Verdict.QUIET);
        assertThat(mv.favourableHit()).isEmpty();
        assertThat(mv.obstructiveHit()).isEmpty();
        assertThat(mv.lords()).isEmpty();
        assertThat(mv.transits()).isEmpty();
    }

    @Test
    void eachVerdictIsReconstructableFromTheCarriedInputs() {
        var dasha = dashaWith(Map.of(
                2, Set.of(Graha.VENUS), 7, Set.of(Graha.VENUS, Graha.JUPITER),
                1, Set.of(Graha.SUN)));
        var transit = transitSupporting(Set.of(7), Set.of(11));

        for (Matter matter : Matter.values()) {
            MatterVerdict mv = VerdictRule.evaluate(matter, dasha, transit);

            // favourableHit / obstructiveHit are exactly the activated houses of each group
            assertThat(mv.favourableHit()).isEqualTo(
                    matter.favourable().stream().filter(h -> dasha.strengthOf(h) > 0)
                            .collect(java.util.stream.Collectors.toSet()));
            assertThat(mv.obstructiveHit()).isEqualTo(
                    matter.obstructive().stream().filter(h -> dasha.strengthOf(h) > 0)
                            .collect(java.util.stream.Collectors.toSet()));

            int favStrength = mv.favourableHit().stream().mapToInt(dasha::strengthOf).sum();
            int obsStrength = mv.obstructiveHit().stream().mapToInt(dasha::strengthOf).sum();
            boolean triggered = mv.favourableHit().stream().anyMatch(transit.supported()::contains);
            assertThat(mv.verdict()).isEqualTo(VerdictRule.classify(
                    mv.favourableHit().isEmpty(), mv.obstructiveHit().isEmpty(),
                    favStrength, obsStrength, triggered));
        }
    }
}
