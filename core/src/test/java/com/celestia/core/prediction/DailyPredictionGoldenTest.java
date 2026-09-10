package com.celestia.core.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.chart.NatalChartFactory;
import com.celestia.core.dasha.DashaTimeline;
import com.celestia.core.golden.GoldenChart;
import com.celestia.core.judgement.SignificatorTable;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EphemerisResult;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * SPEC-006 SC-002 / SC-003 / SC-004: the worked daily reading reproduces — the
 * dasha significators, the transit contribution, and every matter's verdict.
 */
@EnabledIf("hasEphemerisData")
class DailyPredictionGoldenTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    private static GoldenChart golden() {
        for (GoldenChart c : GoldenChart.loadAll()) {
            if (c.daily() != null) {
                return c;
            }
        }
        throw new IllegalStateException("no golden chart carries a daily block");
    }

    @TestFactory
    java.util.List<DynamicNode> theWorkedDailyReadingReproduces() {
        GoldenChart chart = golden();
        GoldenChart.DailyRef ref = chart.daily();

        var positions = new SwissEphemerisPositionProvider();
        NatalChart natal = new NatalChartFactory(positions, new SwissEphemerisHouseProvider())
                .cast(new BirthData(chart.instant(), chart.latitude(), chart.longitude()));
        SignificatorTable table = SignificatorTable.of(natal);
        DashaTimeline timeline = DashaTimeline.from(
                natal.birthData().instant(), natal.position(Graha.MOON).longitude(),
                natal.accuracy(), natal.engineVersion());
        Instant t = ref.referenceInstant();

        DashaSignificators dasha = DashaSignificators.compute(table, timeline, t);

        EphemerisResult atRef = positions.positions(t);
        EphemerisResult before = positions.positions(t.minus(Duration.ofHours(12)));
        EphemerisResult after = positions.positions(t.plus(Duration.ofHours(12)));
        TransitContribution transit = TransitContribution.compute(
                table,
                atRef.position(Graha.MOON).longitude(), atRef.position(Graha.SUN).longitude(),
                before.position(Graha.MOON).longitude(), after.position(Graha.MOON).longitude());

        var checks = new java.util.ArrayList<DynamicNode>();

        checks.add(DynamicTest.dynamicTest("running lords", () ->
                assertThat(dasha.running().stack().stream().map(p -> p.lord().name()).toList())
                        .isEqualTo(ref.runningLords())));
        checks.add(DynamicTest.dynamicTest("per-lord significations", () -> {
            Map<Graha, Map<Integer, Set<Integer>>> actual = new HashMap<>();
            dasha.significationsByLord().forEach((lord, gs) -> {
                Map<Integer, Set<Integer>> houses = new HashMap<>();
                gs.houses().forEach((h, steps) ->
                        houses.put(h, steps.stream().map(com.celestia.core.judgement.Step::rank)
                                .collect(Collectors.toSet())));
                actual.put(lord, houses);
            });
            assertThat(actual).isEqualTo(ref.significationsByLord());
        }));
        checks.add(DynamicTest.dynamicTest("activated houses + strengths", () -> {
            Map<Integer, Integer> actual = dasha.activated().stream()
                    .collect(Collectors.toMap(ActivatedHouse::house, ActivatedHouse::strength));
            Map<Integer, Integer> expected = ref.activated().stream()
                    .collect(Collectors.toMap(GoldenChart.ActivatedRef::house, GoldenChart.ActivatedRef::strength));
            assertThat(actual).isEqualTo(expected);
        }));
        checks.add(DynamicTest.dynamicTest("dasha within-day flag", () ->
                assertThat(dasha.lordChangesWithinDay()).isEqualTo(ref.lordChangesWithinDay())));

        checks.add(DynamicTest.dynamicTest("transit sub lords + supports (SC-003)", () -> {
            assertThat(transit.moonChain().subLord().name()).isEqualTo(ref.moonSubLord());
            assertThat(transit.sunChain().subLord().name()).isEqualTo(ref.sunSubLord());
            assertThat(transit.moonSupports()).isEqualTo(ref.moonSupports());
            assertThat(transit.sunSupports()).isEqualTo(ref.sunSupports());
            assertThat(transit.moonSubLordChangesWithinDay()).isEqualTo(ref.moonSubLordChangesWithinDay());
        }));

        for (Matter matter : Matter.values()) {
            GoldenChart.VerdictRef v = ref.verdicts().get(matter.name());
            MatterVerdict mv = VerdictRule.evaluate(matter, dasha, transit);
            checks.add(DynamicTest.dynamicTest(matter.name() + " verdict", () -> {
                assertThat(mv.verdict().name()).isEqualTo(v.verdict());
                assertThat(mv.favourableHit()).isEqualTo(v.favourableHit());
                assertThat(mv.obstructiveHit()).isEqualTo(v.obstructiveHit());
                assertThat(mv.lords().stream().map(Enum::name).collect(Collectors.toSet()))
                        .isEqualTo(Set.copyOf(v.lords()));
                assertThat(mv.transits().stream().map(Enum::name).collect(Collectors.toSet()))
                        .isEqualTo(Set.copyOf(v.transits()));
            }));
        }

        return checks;
    }
}
