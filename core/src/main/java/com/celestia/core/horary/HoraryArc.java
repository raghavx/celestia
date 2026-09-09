package com.celestia.core.horary;

import com.celestia.core.lordage.LordChain;
import com.celestia.core.lordage.Sign;
import com.celestia.ephemeris.Graha;
import org.apache.commons.numbers.fraction.BigFraction;

/**
 * One of the 249 KP horary arcs: the zodiac arc a querent's number denotes, its
 * sign, and the KP lord chain of its interior.
 *
 * <p>The 249 arcs are the 243 nakshatra sub-lord divisions (SPEC-001
 * {@code VimshottariPartition.subDivisions()}), each split wherever it crosses a
 * 30&deg; sign boundary. The <b>sub lord</b> is constant across a whole arc — it
 * is the KP determinant of the horary answer regardless of where in the arc the
 * Ascendant is placed. Source: {@code core/REFERENCES.md}.
 *
 * @param number 1..249, in increasing-longitude order
 * @param start inclusive sidereal longitude, degrees, exact
 * @param end exclusive sidereal longitude, degrees, exact
 * @param sign the single sign the arc lies in
 * @param lordChain the KP lord chain of the arc's midpoint
 */
public record HoraryArc(
        int number, BigFraction start, BigFraction end, Sign sign, LordChain lordChain) {

    private static final BigFraction HALF = BigFraction.of(1, 2);

    public HoraryArc {
        if (number < 1 || number > 249) {
            throw new IllegalArgumentException("horary number out of 1..249: " + number);
        }
        if (start.compareTo(end) >= 0) {
            throw new IllegalArgumentException("arc start must be < end: " + start + " .. " + end);
        }
        Sign atMidpoint = Sign.at(start.add(end).multiply(HALF).doubleValue());
        if (atMidpoint != sign) {
            throw new IllegalArgumentException(
                    "sign " + sign + " != Sign.at(midpoint) " + atMidpoint);
        }
    }

    public double startDeg() {
        return start.doubleValue();
    }

    public double endDeg() {
        return end.doubleValue();
    }

    public double midpointDeg() {
        return start.add(end).multiply(HALF).doubleValue();
    }

    /** The sub lord — constant over the whole arc; the KP determinant of the answer. */
    public Graha subLord() {
        return lordChain.subLord();
    }

    /** Whether {@code longitudeDeg} falls in this arc, half-open {@code [startDeg, endDeg)}. */
    public boolean contains(double longitudeDeg) {
        return longitudeDeg >= startDeg() && longitudeDeg < endDeg();
    }
}
