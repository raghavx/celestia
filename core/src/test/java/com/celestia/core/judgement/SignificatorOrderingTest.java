package com.celestia.core.judgement;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.chart.NatalChart;
import com.celestia.ephemeris.Graha;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC-003: significators are ordered strongest step first, then Graha ordinal. */
class SignificatorOrderingTest {

    @Test
    void comparatorOrdersByStrongestStepThenGrahaOrdinal() {
        Significator starOfOccupant = new Significator(Graha.SATURN, 5, java.util.Set.of(Step.STAR_OF_OCCUPANT));
        Significator occupant = new Significator(Graha.KETU, 5, java.util.Set.of(Step.OCCUPANT));
        Significator ownerSun = new Significator(Graha.SUN, 5, java.util.Set.of(Step.OWNER));
        Significator ownerMoon = new Significator(Graha.MOON, 5, java.util.Set.of(Step.STAR_OF_OWNER, Step.OWNER));

        // step 1 (Saturn) beats step 2 (Ketu) despite Ketu's lower enum ordinal
        assertThat(Significator.BY_STRENGTH.compare(starOfOccupant, occupant)).isNegative();
        // Moon's strongest is STAR_OF_OWNER (3) -> beats pure OWNER (4)
        assertThat(Significator.BY_STRENGTH.compare(ownerMoon, ownerSun)).isNegative();
        // same strongest step -> Graha ordinal (SUN=2) before (MOON=3)
        Significator ownerSun4 = new Significator(Graha.SUN, 5, java.util.Set.of(Step.OWNER));
        Significator ownerMerc4 = new Significator(Graha.MERCURY, 5, java.util.Set.of(Step.OWNER));
        assertThat(Significator.BY_STRENGTH.compare(ownerSun4, ownerMerc4)).isNegative();
    }

    @Test
    void everyHouseListIsMonotoneUnderTheComparator() {
        Map<Graha, Double> lons = new LinkedHashMap<>();
        double d = 11.0;
        for (Graha g : Graha.values()) {
            lons.put(g, d);
            d += 37.9;
        }
        NatalChart chart = SyntheticChart.of(lons, SyntheticChart.equalCusps(4.0));
        SignificatorTable table = SignificatorTable.of(chart);

        for (int h = 1; h <= 12; h++) {
            List<Significator> list = table.houseSignificators(h).significators();
            for (int i = 1; i < list.size(); i++) {
                assertThat(Significator.BY_STRENGTH.compare(list.get(i - 1), list.get(i)))
                        .as("house %d position %d ordered before %d", h, i - 1, i)
                        .isLessThanOrEqualTo(0);
                // strict: strongest step is non-decreasing in rank
                assertThat(list.get(i - 1).strongestStep().rank())
                        .isLessThanOrEqualTo(list.get(i).strongestStep().rank());
            }
        }
    }
}
