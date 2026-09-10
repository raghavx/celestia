package com.celestia.core.horary;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.golden.GoldenChart;
import com.celestia.core.judgement.SignificatorTable;
import com.celestia.core.judgement.Step;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHoraryHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * SPEC-005 SC-002 / SC-003: the worked horary case reproduces — cusp 1 == the
 * number's Ascendant, the twelve cuspal sub lords and nine placements match, and
 * the SPEC-003 significators run on the horary chart reproduce the reference.
 */
@EnabledIf("hasEphemerisData")
class HoraryGoldenTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @TestFactory
    List<DynamicNode> theWorkedHoraryCaseReproduces() {
        GoldenChart.HoraryRef ref = null;
        for (GoldenChart chart : GoldenChart.loadAll()) {
            if (chart.horary() != null) {
                ref = chart.horary();
            }
        }
        assertThat(ref).as("a golden chart carries a horary block").isNotNull();

        var factory = new HoraryChartFactory(
                new SwissEphemerisPositionProvider(), new SwissEphemerisHoraryHouseProvider());
        BirthData judgment = new BirthData(ref.judgmentInstant(), ref.latitude(), ref.longitude());
        NatalChart chart = factory.cast(ref.number(), judgment);
        GoldenChart.HoraryRef r = ref;

        List<DynamicNode> checks = new ArrayList<>();

        checks.add(DynamicTest.dynamicTest("cusp 1 == the number's Ascendant", () ->
                assertThat(chart.cusp(1).longitude())
                        .isCloseTo(r.ascendant().longitude(), Offset.offset(1e-4))));

        for (int h = 1; h <= 12; h++) {
            int house = h;
            checks.add(DynamicTest.dynamicTest("cusp " + house + " sub lord", () ->
                    assertThat(chart.cusp(house).subLord().name())
                            .isEqualTo(r.cusps().get(house - 1).chain().subLord())));
        }

        for (Graha g : Graha.values()) {
            checks.add(DynamicTest.dynamicTest(g.name() + " placement", () -> {
                var placement = chart.placement(g);
                var expected = r.placements().get(g);
                assertThat(placement.bhava()).as("bhava of %s", g).isEqualTo(expected.bhava());
                assertThat(placement.rasiHouse()).as("rasi house of %s", g).isEqualTo(expected.rasiHouse());
            }));
        }

        checks.add(DynamicTest.dynamicTest("SPEC-003 significators reproduce", () -> {
            SignificatorTable table = SignificatorTable.of(chart);
            for (int h = 1; h <= 12; h++) {
                var actual = table.houseSignificators(h).significators();
                var expected = r.significatorsByHouse().get(h);
                assertThat(actual.stream().map(s -> s.graha().name()).toList())
                        .as("house %d graha order", h)
                        .isEqualTo(expected.stream().map(GoldenChart.SigRef::graha).toList());
                for (int i = 0; i < expected.size(); i++) {
                    Set<Integer> ranks = actual.get(i).steps().stream()
                            .map(Step::rank).collect(Collectors.toSet());
                    assertThat(ranks).as("house %d %s steps", h, expected.get(i).graha())
                            .isEqualTo(expected.get(i).steps());
                }
            }
        }));

        return checks;
    }
}
