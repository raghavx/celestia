package com.celestia.core.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * SPEC-006 SC-004 / FR-010: the v1 verdict rule is a total function whose four
 * outcomes partition the input space.
 */
class VerdictRulePropertyTest {

    @Property(tries = 3000)
    void classifyIsTotalAndMatchesTheFourRowTable(
            @ForAll boolean favActiveEmpty,
            @ForAll boolean obsActiveEmpty,
            @ForAll @IntRange(min = 0, max = 25) int favStrength,
            @ForAll @IntRange(min = 0, max = 25) int obsStrength,
            @ForAll boolean favTriggered) {

        Verdict v = VerdictRule.classify(
                favActiveEmpty, obsActiveEmpty, favStrength, obsStrength, favTriggered);
        assertThat(v).isNotNull();

        if (favActiveEmpty && obsActiveEmpty) {
            assertThat(v).isEqualTo(Verdict.QUIET);
            return;
        }
        // not QUIET from here
        assertThat(v).isNotEqualTo(Verdict.QUIET);

        if (obsStrength > favStrength) {
            assertThat(v).isEqualTo(Verdict.UNFAVOURABLE);
            return;
        }
        // not UNFAVOURABLE from here (strict threshold)
        assertThat(v).isNotEqualTo(Verdict.UNFAVOURABLE);

        if (!favActiveEmpty && favTriggered) {
            assertThat(v).isEqualTo(Verdict.FAVOURABLE);
        } else {
            assertThat(v).isEqualTo(Verdict.MIXED);
        }
    }

    @Property(tries = 2000)
    void favourableAlwaysRequiresAnActiveFavourableHouseAndATrigger(
            @ForAll @IntRange(min = 0, max = 25) int favStrength,
            @ForAll @IntRange(min = 0, max = 25) int obsStrength,
            @ForAll boolean obsActiveEmpty,
            @ForAll boolean favTriggered) {
        // favActiveEmpty == true  =>  never FAVOURABLE
        Verdict v = VerdictRule.classify(true, obsActiveEmpty, favStrength, obsStrength, favTriggered);
        assertThat(v).isNotEqualTo(Verdict.FAVOURABLE);
    }
}
