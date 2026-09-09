package com.celestia.core.judgement;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.chart.NatalChart;
import com.celestia.ephemeris.Graha;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.Size;

/**
 * SC-002: the per-graha table is the strict inverse of the twelve per-house
 * lists — for every graha and house, {@code grahaSignificators(g).signifies(h)}
 * iff {@code houseSignificators(h).signifies(g)}, with identical step sets.
 */
class SignificatorTransposePropertyTest {

    @Property(tries = 2000)
    void perGrahaIsTheStrictTransposeOfPerHouse(
            @ForAll @Size(9) List<@DoubleRange(min = 0.0, max = 360.0, maxIncluded = false) Double> longitudes,
            @ForAll @DoubleRange(min = 0.0, max = 360.0, maxIncluded = false) double ascendant) {
        Map<Graha, Double> lons = new EnumMap<>(Graha.class);
        Graha[] grahas = Graha.values();
        for (int i = 0; i < grahas.length; i++) {
            lons.put(grahas[i], longitudes.get(i));
        }
        NatalChart chart = SyntheticChart.of(lons, SyntheticChart.equalCusps(ascendant));
        SignificatorTable table = SignificatorTable.of(chart);

        for (Graha g : grahas) {
            GrahaSignificators gs = table.grahaSignificators(g);
            for (int h = 1; h <= 12; h++) {
                HouseSignificators hs = table.houseSignificators(h);
                assertThat(gs.signifies(h))
                        .as("%s / house %d membership", g, h)
                        .isEqualTo(hs.signifies(g));
                assertThat(gs.stepsFor(h))
                        .as("%s / house %d steps", g, h)
                        .isEqualTo(hs.stepsFor(g));
            }
        }
    }
}
