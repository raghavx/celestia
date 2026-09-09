package com.celestia.core.dasha;

import com.celestia.ephemeris.Graha;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.numbers.fraction.BigFraction;

/**
 * The Vimshottari nine-way split: an amount {@code total} (an arc in degrees, or a
 * duration in seconds — the unit is the caller's) divided among the nine grahas in
 * Vimshottari order <b>starting from {@code fromLord}</b>, each slice proportional
 * to that graha's dasha years (Ketu 7 … Mercury 17, total 120).
 *
 * <p>Boundaries are exact ({@link BigFraction}); the slices sum to {@code total}
 * with no rounding. This is the same rule the nakshatra sub-lord partition uses
 * (see {@link VimshottariPartition}); source: {@code core/REFERENCES.md}.
 */
public final class VimshottariSplit {

    private static final BigFraction PER_CYCLE = BigFraction.of(1, Graha.CYCLE_YEARS);

    private VimshottariSplit() {}

    /** One slice of a split: the ruling graha and its exact span in the input unit. */
    public record Portion(Graha lord, BigFraction span) {}

    /**
     * The nine portions of {@code total}, in Vimshottari order from {@code fromLord}.
     *
     * @throws IllegalArgumentException if {@code total} is not positive
     */
    public static List<Portion> of(BigFraction total, Graha fromLord) {
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("split total must be positive: " + total);
        }
        List<Portion> portions = new ArrayList<>(9);
        Graha lord = fromLord;
        for (int i = 0; i < 9; i++) {
            portions.add(new Portion(lord, total.multiply(PER_CYCLE).multiply(lord.years())));
            lord = lord.next();
        }
        return portions;
    }
}
