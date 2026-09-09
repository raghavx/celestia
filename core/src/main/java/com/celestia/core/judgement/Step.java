package com.celestia.core.judgement;

/**
 * The four KP significator steps, in decreasing order of strength.
 *
 * <ol>
 *   <li>{@link #STAR_OF_OCCUPANT} — a graha in the star of a (effective) occupant of the house</li>
 *   <li>{@link #OCCUPANT} — a (effective) occupant of the house</li>
 *   <li>{@link #STAR_OF_OWNER} — a graha in the star of the house owner</li>
 *   <li>{@link #OWNER} — the house owner</li>
 * </ol>
 *
 * Source: {@code core/REFERENCES.md} (K. S. Krishnamurti, four-fold significators).
 */
public enum Step {
    STAR_OF_OCCUPANT,
    OCCUPANT,
    STAR_OF_OWNER,
    OWNER;

    /** 1..4, where 1 is strongest. */
    public int rank() {
        return ordinal() + 1;
    }
}
