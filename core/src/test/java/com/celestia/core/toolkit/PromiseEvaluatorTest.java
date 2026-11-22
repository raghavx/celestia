package com.celestia.core.toolkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.judgement.SignificatorTable;
import com.celestia.core.lordage.KpLordage;
import com.celestia.core.lordage.Nakshatra;
import com.celestia.ephemeris.Graha;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * T022 — the evaluator against a hand-built rule book (SPEC-010 FR-004/005/009).
 *
 * <h2>The chart</h2>
 *
 * research.md &sect;3's prototype check: the 1st cuspal sub lord is Venus, Venus
 * sits in Pushya so its star lord is Saturn, and Saturn occupies house 7 while
 * owning houses 10 and 11. House 1 must then connect to exactly {7, 10, 11} —
 * 7 because the star lord occupies it, 10 and 11 because the star lord owns
 * them. The preconditions are asserted, not assumed, so a change in the
 * significator rules shows up here as a precondition failure rather than a
 * confusing expectation mismatch.
 */
class PromiseEvaluatorTest {

    private static final double VENUS_IN_PUSHYA = 95.0;
    private static final Nakshatra PUSHYA = Nakshatra.PUSHYA;

    private static PromiseEvaluator evaluator;
    private static NatalChart chart;
    private static SignificatorTable significators;

    @BeforeAll
    static void setUp() {
        evaluator = new PromiseEvaluator(KpToolkit.load(PromiseFixtureSupport.write()));
        chart = prototypeChart(Set.of());
        significators = SignificatorTable.of(chart);
    }

    // ------------------------------------------------------------- the chart

    /**
     * An ascendant in Aries whose sub lord is Venus, with Venus in Pushya and
     * Saturn in house 7. Searched rather than hard-coded: the sub-lord boundaries
     * are exact fractions, so a literal would be a magic number nobody could
     * check by eye.
     */
    private static NatalChart prototypeChart(Set<Graha> retrograde) {
        double asc = ascendantWithSubLord(Graha.VENUS)
                .orElseThrow(() -> new AssertionError("no Aries ascendant has Venus as its sub lord"));

        Map<Graha, Double> longitudes = new EnumMap<>(Graha.class);
        // Spread the rest around, away from Pushya and from house 7, so they add
        // nothing to Venus's own significations.
        longitudes.put(Graha.SUN, 15.0);
        longitudes.put(Graha.MOON, 45.0);
        longitudes.put(Graha.MARS, 75.0);
        longitudes.put(Graha.MERCURY, 135.0);
        longitudes.put(Graha.JUPITER, 165.0);
        longitudes.put(Graha.VENUS, VENUS_IN_PUSHYA);
        longitudes.put(Graha.SATURN, asc + 190.0); // inside house 7 (asc+180 .. asc+210)
        longitudes.put(Graha.RAHU, 255.0);
        longitudes.put(Graha.KETU, 75.0 + 180.0);

        return SyntheticChart.of(longitudes, SyntheticChart.equalCusps(asc), retrograde);
    }

    private static Optional<Double> ascendantWithSubLord(Graha subLord) {
        for (int step = 0; step < 3000; step++) {
            double asc = step * 0.01; // 0 .. 30, staying inside Aries
            if (asc >= 30.0) {
                break;
            }
            if (KpLordage.chainFor(asc).subLord() == subLord) {
                return Optional.of(asc);
            }
        }
        return Optional.empty();
    }

    @Test
    void thePrototypeChartHasThePreconditionsResearchAssumed() {
        assertThat(chart.cuspSubLord(1)).isEqualTo(Graha.VENUS);
        assertThat(KpLordage.chainFor(chart.position(Graha.VENUS).longitude()).nakshatra())
                .isEqualTo(PUSHYA);
        assertThat(KpLordage.chainFor(chart.position(Graha.VENUS).longitude()).starLord())
                .isEqualTo(Graha.SATURN);
        assertThat(chart.placement(Graha.SATURN).bhava()).isEqualTo(7);
        assertThat(chart.cusp(10).lordChain().signLord()).isEqualTo(Graha.SATURN);
        assertThat(chart.cusp(11).lordChain().signLord()).isEqualTo(Graha.SATURN);
    }

    // ------------------------------------------------- FR-004 connected houses

    @Test
    void houseOneConnectsThroughItsStarLordToExactlySevenTenEleven() {
        HousePromise promise = evaluator.evaluateHouse(chart, significators, 1, false);
        assertThat(promise.connectedHouses()).containsExactly(7, 10, 11);
        assertThat(promise.cuspSubLord()).isEqualTo(Graha.VENUS);
        assertThat(promise.starLord()).isEqualTo(Graha.SATURN);
    }

    // --------------------------------------------------- FR-005 classification

    @Test
    void allHousesConnectedAndNoConditionIsPromised() {
        List<PromiseHit> hits = hitsFor(1);
        PromiseHit hit = byRuleId(hits, PromiseFixtureSupport.PROMISED_ID);
        assertThat(hit.status()).isEqualTo(PromiseHit.Status.PROMISED);
        assertThat(hit.connected()).containsExactly(7, 10, 11);
        assertThat(hit.missing()).isEmpty();
    }

    @Test
    void allHousesConnectedWithAConditionIsNeverAFirmPromise() {
        PromiseHit hit = byRuleId(hitsFor(1), PromiseFixtureSupport.CONDITION_ID);
        assertThat(hit.status()).isEqualTo(PromiseHit.Status.CONDITION_TO_CHECK);
        assertThat(hit.rule().condition()).isEqualTo("+ Saturn");
    }

    @Test
    void aPartlyConnectedRuleIsWithheldUnlessAskedFor() {
        assertThat(hitsFor(1))
                .noneMatch(h -> h.rule().id().equals(PromiseFixtureSupport.PARTIAL_ID));

        HousePromise withPartial = evaluator.evaluateHouse(chart, significators, 1, true);
        PromiseHit hit = byRuleId(withPartial.hits(), PromiseFixtureSupport.PARTIAL_ID);
        assertThat(hit.status()).isEqualTo(PromiseHit.Status.PARTLY_INDICATED);
        assertThat(hit.connected()).containsExactly(7, 10);
        assertThat(hit.missing()).containsExactly(12);
    }

    @Test
    void aRuleWithNoHousesIsKeptButNeverMatchedAutomatically() {
        // FR-003: the author wrote a condition, not a formula. Inventing houses
        // for it would be the model's job, and the constitution forbids that.
        assertThat(evaluator.evaluateHouse(chart, significators, 1, true).hits())
                .noneMatch(h -> h.rule().id().equals(PromiseFixtureSupport.UNMATCHABLE_ID));
    }

    @Test
    void firmHitsAreReportedBeforeConditionalAndPartialOnes() {
        List<PromiseHit.Status> order = evaluator.evaluateHouse(chart, significators, 1, true).hits().stream()
                .map(PromiseHit::status)
                .toList();
        assertThat(order).isSortedAccordingTo(java.util.Comparator.comparingInt(Enum::ordinal));
    }

    @Test
    void rulesJudgedFromAnotherHouseDoNotLeakIn() {
        assertThat(hitsFor(1)).allSatisfy(hit -> assertThat(hit.house()).isEqualTo(1));
    }

    // ---------------------------------------------------- FR-004 retrograde

    @Test
    void aRetrogradeSubLordOrStarLordIsFlaggedForTheReply() {
        NatalChart retro = prototypeChart(Set.of(Graha.SATURN));
        HousePromise promise =
                evaluator.evaluateHouse(retro, SignificatorTable.of(retro), 1, false);
        assertThat(promise.starLordRetrograde()).isTrue();
        assertThat(promise.cuspSubLordRetrograde()).isFalse();
        assertThat(promise.retrogradeCaveat()).isTrue();

        assertThat(evaluator.evaluateHouse(chart, significators, 1, false).retrogradeCaveat())
                .isFalse();
    }

    // ------------------------------------------------- FR-010a sensitivity

    @Test
    void theHousePromiseCarriesTheMostSevereClassItDrewOn() {
        assertThat(evaluator.evaluateHouse(chart, significators, 1, false).sensitivity())
                .isEqualTo(Sensitivity.HEALTH);
    }

    @Test
    void aSelfHarmRuleIsMatchedAndClassifiedLikeAnyOtherHealthRule() {
        // FR-011: no separate class, no special handling - it is reported with
        // its status and carries the health advisory.
        PromiseHit hit = byRuleId(hitsFor(1), PromiseFixtureSupport.SUICIDE_ID);
        assertThat(hit.status()).isEqualTo(PromiseHit.Status.PROMISED);
        assertThat(hit.sensitivity()).isEqualTo(Sensitivity.HEALTH);
        assertThat(hit.sensitivity().note()).contains("qualified doctor");
    }

    // ------------------------------------------------------ FR-009 determinism

    @Test
    void theSameChartAndToolkitYieldTheSameRulesEveryTime() {
        List<String> first = ruleIds(evaluator.evaluate(chart));
        List<String> second = ruleIds(evaluator.evaluate(chart));
        assertThat(second).isEqualTo(first);
    }

    @Test
    void everyHouseIsEvaluatedAndNothingIsStored() {
        List<HousePromise> promises = evaluator.evaluate(chart);
        assertThat(promises).hasSize(12);
        assertThat(promises.stream().map(HousePromise::house).toList())
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    }

    @Test
    void aSecondPersonsChartIsJudgedWithoutDisturbingTheFirst() {
        // FR-012: the evaluator takes any chart and keeps nothing, which is what
        // lets a supplied chart live only as long as the reply.
        NatalChart other = SyntheticChart.of(
                allAt(200.0), SyntheticChart.equalCusps(100.0), Set.of());
        evaluator.evaluate(other);
        assertThat(evaluator.evaluateHouse(chart, significators, 1, false).connectedHouses())
                .containsExactly(7, 10, 11);
    }

    @Test
    void anImpossibleHouseIsRejected() {
        assertThatThrownBy(() -> evaluator.evaluateHouse(chart, significators, 0, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.evaluateHouse(chart, significators, 13, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------- SC-007 against real data

    @Test
    @EnabledIf("com.celestia.core.toolkit.RealToolkit#isImported")
    void aTwelveHouseReadingAgainstTheRealRuleBookStaysWellUnderFiftyMilliseconds() {
        // SC-007 / plan.md Performance Goals. Meaningless against the small
        // fixture, so it runs against the real ~2,000 rules.
        PromiseEvaluator real = PromiseEvaluator.bundled();
        for (int warmUp = 0; warmUp < 50; warmUp++) {
            real.evaluate(chart, significators, false);
        }
        int runs = 200;
        long start = System.nanoTime();
        for (int i = 0; i < runs; i++) {
            real.evaluate(chart, significators, false);
        }
        double averageMillis = (System.nanoTime() - start) / 1_000_000.0 / runs;
        // The mean, not a single call: one unlucky GC pause must not fail CI.
        assertThat(averageMillis)
                .as("mean milliseconds for a 12-house reading over %d runs", runs)
                .isLessThan(50.0);
    }

    // ---------------------------------------------------------------- helpers

    private static Map<Graha, Double> allAt(double longitude) {
        Map<Graha, Double> longitudes = new EnumMap<>(Graha.class);
        for (Graha graha : Graha.values()) {
            longitudes.put(graha, longitude);
        }
        return longitudes;
    }

    private static List<PromiseHit> hitsFor(int house) {
        return evaluator.evaluateHouse(chart, significators, house, false).hits();
    }

    private static PromiseHit byRuleId(List<PromiseHit> hits, String ruleId) {
        return hits.stream()
                .filter(h -> h.rule().id().equals(ruleId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no hit for rule " + ruleId + " in " + ruleIdsOf(hits)));
    }

    private static List<String> ruleIdsOf(List<PromiseHit> hits) {
        return hits.stream().map(h -> h.rule().id()).toList();
    }

    private static List<String> ruleIds(List<HousePromise> promises) {
        return promises.stream()
                .flatMap(p -> p.hits().stream())
                .map(h -> h.rule().id())
                .toList();
    }
}
