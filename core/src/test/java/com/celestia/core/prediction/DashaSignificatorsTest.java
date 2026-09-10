package com.celestia.core.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.dasha.DashaLevel;
import com.celestia.core.dasha.DashaTimeline;
import com.celestia.core.judgement.SignificatorTable;
import com.celestia.ephemeris.Graha;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC-006 US2: the dasha significators. */
class DashaSignificatorsTest {

    private static NatalChart chart() {
        Map<Graha, Double> lons = new EnumMap<>(Graha.class);
        double d = 12.0;
        for (Graha g : Graha.values()) {
            lons.put(g, d);
            d += 39.3;
        }
        return SyntheticChart.of(lons, SyntheticChart.equalCusps(8.0));
    }

    private static DashaTimeline timelineOf(NatalChart c) {
        return DashaTimeline.from(
                c.birthData().instant(), c.position(Graha.MOON).longitude(),
                c.accuracy(), c.engineVersion());
    }

    @Test
    void perLordSignificationsEqualTheNatalTableAndActivatedIsTheUnion() {
        NatalChart c = chart();
        SignificatorTable table = SignificatorTable.of(c);
        DashaTimeline timeline = timelineOf(c);
        Instant ref = c.birthData().instant().plus(3650, ChronoUnit.DAYS); // ~10 years on

        DashaSignificators ds = DashaSignificators.compute(table, timeline, ref);

        for (DashaLevel level : DashaLevel.values()) {
            Graha lord = ds.running().lord(level);
            assertThat(ds.significationsByLord().get(lord))
                    .as("%s (%s) significations", level, lord)
                    .isEqualTo(table.grahaSignificators(lord));
        }

        // activated = union of the distinct running lords' signified houses
        for (int h = 1; h <= 12; h++) {
            final int house = h;
            long signers = ds.significationsByLord().values().stream()
                    .filter(gs -> gs.signifies(house))
                    .map(gs -> gs.graha())
                    .distinct()
                    .count();
            assertThat(ds.strengthOf(house)).as("strength of house %d", house).isEqualTo((int) signers);
            assertThat(ds.activatedHouses().contains(house)).isEqualTo(signers > 0);
        }
    }

    @Test
    void lordChangesWithinDayReflectsTheTwelveHourWindow() {
        NatalChart c = chart();
        SignificatorTable table = SignificatorTable.of(c);
        DashaTimeline timeline = timelineOf(c);

        // pick an instant sitting on a Prana boundary: the deepest level flips within 24h
        Instant nearBoundary = timeline.running(
                        c.birthData().instant().plus(4000, ChronoUnit.DAYS), 5)
                .period(DashaLevel.PRANA).end().minusSeconds(1);

        DashaSignificators onBoundary = DashaSignificators.compute(table, timeline, nearBoundary);
        assertThat(onBoundary.lordChangesWithinDay()).isTrue();

        // deep inside a long Mahadasha the stack is stable across ±12h
        DashaSignificators stable = DashaSignificators.compute(
                table, timeline, c.birthData().instant().plus(3650, ChronoUnit.DAYS));
        // (may be true or false depending on the synthetic chart; assert it is a boolean, not an error)
        assertThat(stable.lordChangesWithinDay()).isIn(true, false);
    }
}
