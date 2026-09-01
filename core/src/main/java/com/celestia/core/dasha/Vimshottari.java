package com.celestia.core.dasha;

import java.util.List;

/**
 * The Vimshottari mahadasha sequence and its 120-year cycle.
 *
 * <p>This is the ordering and year allocation that also governs the KP sub-lord
 * partition of every nakshatra. Source: standard KP / Vimshottari dasha tables
 * (see SPEC-001, SPEC-004).
 */
public enum Vimshottari {
    KETU(7),
    VENUS(20),
    SUN(6),
    MOON(10),
    MARS(7),
    RAHU(18),
    JUPITER(16),
    SATURN(19),
    MERCURY(17);

    /** Total years in one full Vimshottari cycle. */
    public static final int CYCLE_YEARS = 120;

    private final int years;

    Vimshottari(int years) {
        this.years = years;
    }

    /** Dasha length of this lord, in years. */
    public int years() {
        return years;
    }

    /** The nine lords in Vimshottari order, starting from Ketu. */
    public static List<Vimshottari> sequence() {
        return List.of(values());
    }

    /** The lord that follows this one in the cycle (wraps around). */
    public Vimshottari next() {
        Vimshottari[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
