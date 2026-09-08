package com.celestia.core.dasha;

import com.celestia.core.lordage.Nakshatra;
import com.celestia.ephemeris.Graha;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.numbers.fraction.BigFraction;

/**
 * The KP sub-lord division: a nakshatra split into nine <i>subs</i>, and each sub
 * split into nine <i>sub-subs</i>, in Vimshottari order and proportional to the
 * dasha years (Ketu 7 &hellip; Mercury 17, total 120).
 *
 * <p>All boundaries are exact ({@link BigFraction}). Source for the rule:
 * {@code core/REFERENCES.md}.
 *
 * <p>The 27 &times; 9 = 243 subs tile {@code [0&deg;, 360&deg;)} exactly. The KP
 * horary table of 249 entries (SPEC-005) is these 243 spans further split at the
 * 12 sign boundaries; this class exposes the exact boundaries that split needs
 * but does not perform it.
 */
public final class VimshottariPartition {

    /** Width of one nakshatra, 13&deg;20'. */
    private static final BigFraction NAK_WIDTH = BigFraction.of(40, 3);

    private static final BigFraction ONE_TWENTIETH = BigFraction.of(1, 120);

    private VimshottariPartition() {}

    /**
     * The nine sub-spans of {@code nakshatra}, in Vimshottari order from its lord,
     * covering {@code [start, start + 13&deg;20')} exactly.
     */
    public static List<Span> subs(Nakshatra nakshatra) {
        BigFraction start = BigFraction.of((long) nakshatra.ordinal() * 40, 3);
        return partition(start, NAK_WIDTH, nakshatra.lord());
    }

    /**
     * All 243 sub-spans across the whole zodiac, in longitude order. The KP horary
     * table of 249 entries (SPEC-005) is these spans further split at the 12 sign
     * boundaries; the exact {@link Span#start()} / {@link Span#end()} make that
     * split lossless.
     */
    public static List<Span> subDivisions() {
        List<Span> all = new ArrayList<>(243);
        for (Nakshatra n : Nakshatra.values()) {
            all.addAll(subs(n));
        }
        return all;
    }

    /**
     * The nine sub-sub-spans of the given sub, in Vimshottari order from
     * {@code subLord}, covering that sub exactly.
     *
     * @throws IllegalArgumentException if {@code subLord} does not rule a sub of
     *     {@code nakshatra} (it always does for a valid KP lord chain)
     */
    public static List<Span> subSubs(Nakshatra nakshatra, Graha subLord) {
        for (Span sub : subs(nakshatra)) {
            if (sub.lord() == subLord) {
                return partition(sub.start(), sub.width(), subLord);
            }
        }
        throw new IllegalArgumentException(subLord + " is not a sub lord of " + nakshatra);
    }

    /** Nine spans from {@code start}, total width {@code width}, lords from {@code firstLord}. */
    private static List<Span> partition(BigFraction start, BigFraction width, Graha firstLord) {
        List<Span> spans = new ArrayList<>(9);
        BigFraction cursor = start;
        Graha lord = firstLord;
        for (int i = 0; i < 9; i++) {
            BigFraction w = width.multiply(ONE_TWENTIETH).multiply(lord.years());
            BigFraction end = cursor.add(w);
            spans.add(new Span(lord, cursor, end));
            cursor = end;
            lord = lord.next();
        }
        return spans;
    }
}
