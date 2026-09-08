package com.celestia.core.lordage;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.golden.GoldenChart;
import com.celestia.ephemeris.Graha;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * The lord chain our engine derives for a longitude must match the chain the
 * independent cross-check tool (pyswisseph) derived for the same longitude.
 * This isolates the KP division logic from the position computation (that is
 * covered end-to-end by the golden-chart suite in US4).
 */
class KpLordageTableTest {

    @TestFactory
    List<DynamicTest> lordChainMatchesGoldenReferenceForEveryGraha() {
        List<DynamicTest> tests = new ArrayList<>();
        for (GoldenChart chart : GoldenChart.loadAll()) {
            for (Graha g : Graha.values()) {
                GoldenChart.Expected e = chart.expected().get(g);
                tests.add(DynamicTest.dynamicTest(chart.id() + " / " + g, () -> {
                    LordChain c = KpLordage.chainFor(e.longitude());
                    assertThat(c.sign().name()).isEqualTo(e.sign());
                    assertThat(c.signLord().name()).isEqualTo(e.signLord());
                    assertThat(c.nakshatra().name()).isEqualTo(e.nakshatra());
                    assertThat(c.pada()).isEqualTo(e.pada());
                    assertThat(c.starLord().name()).isEqualTo(e.starLord());
                    assertThat(c.subLord().name()).isEqualTo(e.subLord());
                    assertThat(c.subSubLord().name()).isEqualTo(e.subSubLord());
                }));
            }
        }
        return tests;
    }

    @org.junit.jupiter.api.Test
    void chainForZeroIsAriesAshwiniPadaOneKetu() {
        LordChain c = KpLordage.chainFor(0.0);
        assertThat(c.sign()).isEqualTo(Sign.ARIES);
        assertThat(c.signLord()).isEqualTo(Graha.MARS);
        assertThat(c.nakshatra()).isEqualTo(Nakshatra.ASHWINI);
        assertThat(c.pada()).isEqualTo(1);
        assertThat(c.starLord()).isEqualTo(Graha.KETU);
        assertThat(c.subLord()).isEqualTo(Graha.KETU);
        assertThat(c.subSubLord()).isEqualTo(Graha.KETU);
    }
}
