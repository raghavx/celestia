package com.celestia.core.dasha;

import com.celestia.ephemeris.Graha;
import org.apache.commons.numbers.fraction.BigFraction;

/**
 * A contiguous arc of the zodiac ruled by one graha, with <b>exact</b> rational
 * endpoints in degrees.
 *
 * <p>Used for the Vimshottari sub and sub-sub partitions. Exact endpoints are
 * what make the tiling guarantees (SC-003, SC-004) literal equalities rather than
 * epsilon comparisons. Callers that do not need exactness use {@link #startDeg()}
 * / {@link #endDeg()}.
 */
public record Span(Graha lord, BigFraction start, BigFraction end) {

    public Span {
        if (start.compareTo(end) >= 0) {
            throw new IllegalArgumentException("span start must be < end: " + start + " .. " + end);
        }
    }

    /** Start longitude as a {@code double}, degrees. */
    public double startDeg() {
        return start.doubleValue();
    }

    /** End longitude as a {@code double}, degrees. */
    public double endDeg() {
        return end.doubleValue();
    }

    /** Exact width of this span, degrees. */
    public BigFraction width() {
        return end.subtract(start);
    }

    /**
     * Whether {@code longitudeDeg} falls in this span, half-open {@code [start, end)}.
     */
    public boolean contains(double longitudeDeg) {
        return longitudeDeg >= startDeg() && longitudeDeg < endDeg();
    }
}
