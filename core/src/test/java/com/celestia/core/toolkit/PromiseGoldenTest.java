package com.celestia.core.toolkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.chart.NatalChartFactory;
import com.celestia.core.golden.GoldenChart;
import com.celestia.core.judgement.SignificatorTable;
import com.celestia.core.judgement.Step;
import com.celestia.core.lordage.KpLordage;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * T024 — a 12-house promise reading for every golden chart (SPEC-010 SC-005).
 *
 * <p>Two jobs. First, it pins the part of a reading that comes from the engine:
 * each house's cuspal sub lord, that sub lord's star lord, the retrograde flags
 * and — the KP judgement step this feature turns on — the houses connected
 * through the star lord. Those are checked against the significator table
 * independently, so a drift in either shows up here.
 *
 * <p>Second, it writes {@code target/promise-readings/<chart>.txt}: the readable
 * snapshot quickstart.md step 5 asks for and the owner marks up during the
 * T048 practitioner review. Matched rules appear in it once the real rule book
 * is imported (T047); until then the chart-derived half stands on its own.
 */
@EnabledIf("hasEphemerisData")
class PromiseGoldenTest {

    private static final Path SNAPSHOT_DIR = Path.of("target", "promise-readings");

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @TestFactory
    List<DynamicNode> everyGoldenChartYieldsATwelveHouseReading() {
        PromiseEvaluator evaluator = new PromiseEvaluator(KpToolkit.load(PromiseFixtureSupport.write()));
        var factory = new NatalChartFactory(new SwissEphemerisPositionProvider(), new SwissEphemerisHouseProvider());

        List<DynamicNode> checks = new ArrayList<>();
        for (GoldenChart golden : GoldenChart.loadAll()) {
            NatalChart chart = factory.cast(new BirthData(golden.instant(), golden.latitude(), golden.longitude()));
            SignificatorTable significators = SignificatorTable.of(chart);
            List<HousePromise> reading = evaluator.evaluate(chart, significators, true);

            checks.add(DynamicTest.dynamicTest(
                    golden.id() + ": twelve houses, each derived from the chart",
                    () -> assertReading(chart, significators, reading)));
            writeSnapshot(golden.id(), reading);
        }
        return checks;
    }

    private static void assertReading(
            NatalChart chart, SignificatorTable significators, List<HousePromise> reading) {
        assertThat(reading).hasSize(12);

        for (HousePromise promise : reading) {
            int house = promise.house();

            // The cuspal sub lord and its star lord are the chart's, not ours.
            assertThat(promise.cuspSubLord())
                    .as("house %d cuspal sub lord", house)
                    .isEqualTo(chart.cuspSubLord(house));
            assertThat(promise.starLord())
                    .as("house %d star lord of the CSL", house)
                    .isEqualTo(KpLordage.chainFor(
                                    chart.position(promise.cuspSubLord()).longitude())
                            .starLord());
            assertThat(promise.cuspSubLordRetrograde())
                    .isEqualTo(chart.position(promise.cuspSubLord()).retrograde());
            assertThat(promise.starLordRetrograde())
                    .isEqualTo(chart.position(promise.starLord()).retrograde());

            // research.md §3: connected == the CSL's STAR_OF_OCCUPANT u STAR_OF_OWNER
            // houses, computed here straight off the significator table.
            var expected = new java.util.TreeSet<Integer>();
            significators
                    .grahaSignificators(promise.cuspSubLord())
                    .houses()
                    .forEach((signified, steps) -> {
                        if (steps.contains(Step.STAR_OF_OCCUPANT) || steps.contains(Step.STAR_OF_OWNER)) {
                            expected.add(signified);
                        }
                    });
            assertThat(promise.connectedHouses())
                    .as("house %d connected through the star lord", house)
                    .containsExactlyElementsOf(expected);

            // Every reported hit must be judged from this house and be consistent
            // with the connected set.
            for (PromiseHit hit : promise.hits()) {
                assertThat(hit.house()).isEqualTo(house);
                assertThat(promise.connectedHouses()).containsAll(hit.connected());
                assertThat(hit.missing()).noneMatch(promise.connectedHouses()::contains);
            }
        }
    }

    /** The readable reading the owner reviews (quickstart.md step 5, T048). */
    private static void writeSnapshot(String chartId, List<HousePromise> reading) {
        StringBuilder out = new StringBuilder();
        out.append("12-house promise reading - ").append(chartId).append('\n');
        out.append("generated by PromiseGoldenTest; rules appear once the toolkit is imported (T047)\n\n");
        for (HousePromise promise : reading) {
            out.append("House ").append(promise.house()).append('\n');
            out.append("  cuspal sub lord : ")
                    .append(promise.cuspSubLord())
                    .append(promise.cuspSubLordRetrograde() ? " (retrograde)" : "")
                    .append('\n');
            out.append("  its star lord   : ")
                    .append(promise.starLord())
                    .append(promise.starLordRetrograde() ? " (retrograde)" : "")
                    .append('\n');
            out.append("  connects to     : ").append(promise.connectedHouses()).append('\n');
            if (promise.retrogradeCaveat()) {
                out.append("  note            : retrograde - weakens or delays the result\n");
            }
            if (promise.hits().isEmpty()) {
                out.append("  rules           : (none)\n");
            } else {
                out.append("  sensitivity     : ").append(promise.sensitivity()).append('\n');
                for (PromiseHit hit : promise.hits()) {
                    out.append("  - [")
                            .append(hit.status())
                            .append("] ")
                            .append(hit.rule().matter())
                            .append("  (houses ")
                            .append(hit.rule().houses())
                            .append(hit.rule().hasCondition() ? ", condition: " + hit.rule().condition() : "")
                            .append(", source ")
                            .append(hit.rule().id())
                            .append(")\n");
                }
            }
            out.append('\n');
        }
        try {
            Files.createDirectories(SNAPSHOT_DIR);
            Files.writeString(SNAPSHOT_DIR.resolve(chartId + ".txt"), out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed writing the promise-reading snapshot for " + chartId, e);
        }
    }
}
