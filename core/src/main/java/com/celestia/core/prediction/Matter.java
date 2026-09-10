package com.celestia.core.prediction;

import java.util.Set;

/**
 * A life matter in the closed KP daily-prediction taxonomy. Each carries the
 * <b>favourable</b> houses (must be well signified) and the <b>obstructive</b>
 * houses (the "negation" houses — typically the 12th-from each favourable house).
 *
 * <p>Sources: K. S. Krishnamurti, <i>KP Readers</i> vols. III–IV (house
 * significations); the 12th-from negation is standard KP. The exact house lists
 * are a <b>v1</b> design choice — see {@code core/REFERENCES.md} and
 * {@code specs/006-daily-prediction/research.md} §1.
 */
public enum Matter {
    MARRIAGE(Set.of(2, 7, 11), Set.of(1, 6, 10)),
    CAREER(Set.of(2, 6, 10, 11), Set.of(1, 5, 9, 12)),
    WEALTH(Set.of(2, 6, 10, 11), Set.of(1, 5, 9, 12)),
    EDUCATION(Set.of(4, 9, 11), Set.of(3, 8, 10)),
    CHILDREN(Set.of(2, 5, 11), Set.of(1, 4, 10)),
    PROPERTY(Set.of(4, 11, 12), Set.of(3, 8, 10)),
    TRAVEL(Set.of(3, 9, 12), Set.of(4, 8)),
    LITIGATION(Set.of(6, 11), Set.of(5, 8, 12)),
    HEALTH_RECOVERY(Set.of(5, 11), Set.of(1, 6, 8, 12));

    private static final String SOURCE = "K. S. Krishnamurti, KP Readers III–IV (house significations); 12th-from negation (v1)";

    private final Set<Integer> favourable;
    private final Set<Integer> obstructive;

    Matter(Set<Integer> favourable, Set<Integer> obstructive) {
        this.favourable = favourable;
        this.obstructive = obstructive;
        if (favourable.isEmpty()) {
            throw new IllegalStateException(name() + ": favourable set is empty");
        }
        if (!java.util.Collections.disjoint(favourable, obstructive)) {
            throw new IllegalStateException(name() + ": favourable and obstructive overlap");
        }
        for (int h : favourable) {
            requireHouse(h);
        }
        for (int h : obstructive) {
            requireHouse(h);
        }
    }

    public Set<Integer> favourable() {
        return favourable;
    }

    public Set<Integer> obstructive() {
        return obstructive;
    }

    public String source() {
        return SOURCE;
    }

    /** Lower-case stable key for the {@code explainHouseGrouping} tool. */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    private static void requireHouse(int h) {
        if (h < 1 || h > 12) {
            throw new IllegalStateException("house out of 1..12: " + h);
        }
    }
}
