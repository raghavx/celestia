package com.celestia.core.judgement;

import com.celestia.ephemeris.Graha;
import java.util.Comparator;
import java.util.Set;

/**
 * One graha that signifies a house, and the KP steps that qualified it.
 *
 * @param graha the significating graha
 * @param house 1..12
 * @param steps the non-empty, immutable set of qualifying steps
 */
public record Significator(Graha graha, int house, Set<Step> steps) {

    /** Order: strongest step first, then Graha ordinal (Vimshottari order). */
    public static final Comparator<Significator> BY_STRENGTH =
            Comparator.comparingInt((Significator s) -> s.strongestStep().rank())
                    .thenComparing(s -> s.graha().ordinal());

    public Significator {
        if (house < 1 || house > 12) {
            throw new IllegalArgumentException("house out of 1..12: " + house);
        }
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("a significator must have at least one step");
        }
        steps = Set.copyOf(steps);
    }

    /** The lowest-ranked (strongest) step present. */
    public Step strongestStep() {
        return steps.stream().min(Comparator.comparingInt(Step::rank)).orElseThrow();
    }
}
