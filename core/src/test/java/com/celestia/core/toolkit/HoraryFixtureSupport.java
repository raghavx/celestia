package com.celestia.core.toolkit;

import com.celestia.core.horary.Horary249;
import com.celestia.core.horary.HoraryArc;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the 249-row horary fixture from the engine itself (SPEC-010 T029).
 *
 * <p>Every sign/star/sub-lord column is read off {@link Horary249}, so the
 * fixture agrees with the engine by construction. That makes the structural
 * half of SC-003 (249 of 249 rows, numbered once each, lords matching) testable
 * today, without the licensed workbook: only the mindset prose is proprietary,
 * and the fixture fills it with a placeholder.
 *
 * <p>The real cross-check — that the <em>workbook's</em> lords agree with the
 * engine's — needs the real table and so waits on T047.
 */
final class HoraryFixtureSupport {

    static final String NAME = "horary-fixture";

    private HoraryFixtureSupport() {}

    /** Write the fixture root and return the classpath root to load it from. */
    static String write() {
        List<List<String>> records = new ArrayList<>(249);
        for (int number = 1; number <= Horary249.count(); number++) {
            HoraryArc arc = Horary249.arc(number);
            records.add(List.of(
                    String.valueOf(number),
                    arc.sign().name(),
                    arc.lordChain().nakshatra().name(),
                    String.valueOf(arc.startDeg()),
                    String.valueOf(arc.endDeg()),
                    arc.lordChain().signLord().name(),
                    arc.lordChain().starLord().name(),
                    arc.subLord().name(),
                    "placeholder mindset for " + number));
        }
        ToolkitFixtures.writeRelease(NAME, 0, 0);
        ToolkitFixtures.writeTsv(NAME, "manifest.tsv", List.of("order", "id", "title", "group", "kind", "sensitivity", "answerable"), List.of());
        ToolkitFixtures.writeTsv(
                NAME,
                "tables/horary-249.tsv",
                List.of("number", "sign", "star", "fromDeg", "toDeg", "signLord", "starLord", "subLord", "mindset"),
                records);
        return ToolkitFixtures.root(NAME);
    }
}
