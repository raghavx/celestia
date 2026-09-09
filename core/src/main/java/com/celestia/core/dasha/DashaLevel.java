package com.celestia.core.dasha;

import java.util.Optional;

/**
 * The five nested Vimshottari period levels, in decreasing size.
 *
 * <ol>
 *   <li>{@link #MAHADASHA} — the major period (7–20 years)</li>
 *   <li>{@link #ANTARDASHA} — also <i>Bhukti</i></li>
 *   <li>{@link #PRATYANTARDASHA} — also <i>Antara</i></li>
 *   <li>{@link #SOOKSHMA}</li>
 *   <li>{@link #PRANA}</li>
 * </ol>
 *
 * Each level subdivides its parent into nine, in Vimshottari order from the
 * parent's lord, proportional to the dasha-year weights. Source:
 * {@code core/REFERENCES.md}.
 */
public enum DashaLevel {
    MAHADASHA,
    ANTARDASHA,
    PRATYANTARDASHA,
    SOOKSHMA,
    PRANA;

    /** 1..5, where 1 is the largest period. */
    public int rank() {
        return ordinal() + 1;
    }

    /** The level with the given rank (1..5). */
    public static DashaLevel ofRank(int rank) {
        if (rank < 1 || rank > 5) {
            throw new IllegalArgumentException("dasha level rank out of 1..5: " + rank);
        }
        return values()[rank - 1];
    }

    /** The next level down, or empty for {@link #PRANA}. */
    public Optional<DashaLevel> child() {
        return this == PRANA ? Optional.empty() : Optional.of(values()[ordinal() + 1]);
    }

    /** The next level up, or empty for {@link #MAHADASHA}. */
    public Optional<DashaLevel> parent() {
        return this == MAHADASHA ? Optional.empty() : Optional.of(values()[ordinal() - 1]);
    }
}
