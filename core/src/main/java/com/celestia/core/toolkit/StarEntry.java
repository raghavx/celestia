package com.celestia.core.toolkit;

import com.celestia.core.lordage.Nakshatra;
import java.util.Set;

/**
 * One row of a star look-up, keyed by nakshatra and the quarters it covers
 * (SPEC-010 FR-006, US3).
 *
 * <p>The two star sheets group their rows by quarter, not one row per quarter —
 * "Krittika 2-3-4Q" is a single row covering three of the four. The import
 * resolves every spelling the workbook uses to a {@link Nakshatra} constant and
 * parses the quarter notation (research.md &sect;6); all 108 nakshatra-quarters
 * must resolve (SC-004).
 *
 * @param nakshatra the resolved nakshatra
 * @param quarters the padas this row covers, 1..4, never empty
 * @param label the workbook's own label for the row, kept verbatim
 * @param primary professions, or body parts, depending on the sheet
 * @param secondary classical professions, or diseases
 */
public record StarEntry(Nakshatra nakshatra, Set<Integer> quarters, String label, String primary, String secondary) {

    public StarEntry {
        if (quarters.isEmpty()) {
            throw new IllegalArgumentException("star entry " + label + ": no quarters");
        }
        for (int q : quarters) {
            if (q < 1 || q > 4) {
                throw new IllegalArgumentException("quarter out of 1..4: " + q);
            }
        }
        quarters = Set.copyOf(quarters);
    }

    /** True when this row covers {@code quarter}. */
    public boolean covers(int quarter) {
        return quarters.contains(quarter);
    }
}
