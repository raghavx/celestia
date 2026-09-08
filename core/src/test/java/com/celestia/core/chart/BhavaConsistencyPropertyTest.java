package com.celestia.core.chart;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.Size;

/** SC-004: bhava assignment is total, unique and contiguous for any valid cusp ring. */
class BhavaConsistencyPropertyTest {

    @Property(tries = 5000)
    void bhavaOfIsTotalAndInRange(
            @ForAll @Size(12) List<@DoubleRange(min = 1.0, max = 50.0) Double> gaps,
            @ForAll @DoubleRange(min = 0.0, max = 360.0) double startCusp,
            @ForAll @DoubleRange(min = 0.0, max = 360.0, maxIncluded = false) double lambda) {
        assertThat(Bhavas.bhavaOf(lambda, validRing(startCusp, gaps))).isBetween(1, 12);
    }

    @Property(tries = 2000)
    void eachCuspStartsItsOwnBhavaAndTheMidpointOfEachArcIsInThatBhava(
            @ForAll @Size(12) List<@DoubleRange(min = 1.0, max = 50.0) Double> gaps,
            @ForAll @DoubleRange(min = 0.0, max = 360.0) double startCusp) {
        List<Double> ring = validRing(startCusp, gaps);
        for (int n = 0; n < 12; n++) {
            // exactly on cusp n+1 -> bhava n+1 (half-open, boundary to the higher side)
            assertThat(Bhavas.bhavaOf(ring.get(n), ring)).as("on cusp %d", n + 1).isEqualTo(n + 1);

            // the midpoint of arc n -> bhava n+1 (well inside, float-safe)
            double arc = forward(ring.get(n), ring.get((n + 1) % 12));
            double mid = (ring.get(n) + arc / 2.0) % 360.0;
            assertThat(Bhavas.bhavaOf(mid, ring)).as("midpoint of arc %d", n + 1).isEqualTo(n + 1);
        }
    }

    @Property(tries = 2000)
    void everyBhavaIsHitExactlyOnceByTheTwelveArcMidpoints(
            @ForAll @Size(12) List<@DoubleRange(min = 1.0, max = 50.0) Double> gaps,
            @ForAll @DoubleRange(min = 0.0, max = 360.0) double startCusp) {
        List<Double> ring = validRing(startCusp, gaps);
        var hits = new java.util.HashSet<Integer>();
        for (int n = 0; n < 12; n++) {
            double arc = forward(ring.get(n), ring.get((n + 1) % 12));
            hits.add(Bhavas.bhavaOf((ring.get(n) + arc / 2.0) % 360.0, ring));
        }
        assertThat(hits).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 12).boxed().toList());
    }

    /** 12 cusps from 12 bounded gaps normalised to sum 360, cumulative from startCusp. */
    private static List<Double> validRing(double startCusp, List<Double> gaps) {
        double total = gaps.stream().mapToDouble(Double::doubleValue).sum();
        List<Double> ring = new ArrayList<>(12);
        double cursor = ((startCusp % 360.0) + 360.0) % 360.0;
        for (int i = 0; i < 12; i++) {
            ring.add(cursor % 360.0);
            cursor = (cursor + gaps.get(i) / total * 360.0) % 360.0;
        }
        return ring;
    }

    private static double forward(double from, double to) {
        double d = (to - from) % 360.0;
        return d < 0 ? d + 360.0 : d;
    }
}
