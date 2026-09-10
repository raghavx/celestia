package com.celestia.core.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.judgement.SignificatorTable;
import com.celestia.core.lordage.KpLordage;
import com.celestia.ephemeris.Graha;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC-006 US3: the v1 sub-lord transit rule. */
class TransitContributionTest {

    private static NatalChart chart() {
        Map<Graha, Double> lons = new EnumMap<>(Graha.class);
        double d = 5.0;
        for (Graha g : Graha.values()) {
            lons.put(g, d);
            d += 41.0;
        }
        return SyntheticChart.of(lons, SyntheticChart.equalCusps(15.0));
    }

    private final SignificatorTable table = SignificatorTable.of(chart());

    @Test
    void aBodySupportsExactlyTheNatalSignificationsOfItsSubLord() {
        double moonLon = 123.45;
        double sunLon = 271.9;
        TransitContribution tc = TransitContribution.compute(table, moonLon, sunLon, moonLon, moonLon);

        assertThat(tc.moonChain()).isEqualTo(KpLordage.chainFor(moonLon));
        assertThat(tc.sunChain()).isEqualTo(KpLordage.chainFor(sunLon));
        assertThat(tc.moonSupports())
                .isEqualTo(table.grahaSignificators(tc.moonChain().subLord()).houses().keySet());
        assertThat(tc.sunSupports())
                .isEqualTo(table.grahaSignificators(tc.sunChain().subLord()).houses().keySet());
        assertThat(tc.supported()).containsAll(tc.moonSupports()).containsAll(tc.sunSupports());
    }

    @Test
    void moonSubLordChangeFlag() {
        // same sub lord at t and t±12h  -> false
        assertThat(TransitContribution.compute(table, 100.0, 200.0, 100.05, 99.95)
                .moonSubLordChangesWithinDay()).isFalse();
        // a far-apart "before" longitude in a different sub -> true
        assertThat(TransitContribution.compute(table, 100.0, 200.0, 100.0, 106.0)
                .moonSubLordChangesWithinDay())
                .isEqualTo(KpLordage.chainFor(106.0).subLord() != KpLordage.chainFor(100.0).subLord());
    }
}
