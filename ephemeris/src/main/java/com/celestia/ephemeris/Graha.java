package com.celestia.ephemeris;

import java.util.List;

/**
 * The nine KP grahas, listed in Vimshottari dasha order starting from Ketu, each
 * carrying its dasha-year weight.
 *
 * <p>These same nine bodies are the Vimshottari lords, so this enum is also the
 * basis of the sub-lord partition of every nakshatra (see
 * {@code com.celestia.core.dasha.VimshottariPartition}). The ordering and the
 * year weights are the standard KP tables; the total is 120 years.
 */
public enum Graha {
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

    Graha(int years) {
        this.years = years;
    }

    /** This graha's Vimshottari dasha length, in years. */
    public int years() {
        return years;
    }

    /** The next graha in Vimshottari order; wraps {@code MERCURY -> KETU}. */
    public Graha next() {
        Graha[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** The nine grahas in Vimshottari order, starting from Ketu. */
    public static List<Graha> vimshottariOrder() {
        return List.of(values());
    }
}
