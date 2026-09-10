package com.celestia.core.horary;

import com.celestia.core.dasha.Span;
import com.celestia.core.dasha.VimshottariPartition;
import com.celestia.core.lordage.KpLordage;
import com.celestia.core.lordage.Sign;
import com.celestia.ephemeris.Graha;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.numbers.fraction.BigFraction;

/**
 * The KP horary 1–249 map: the 243 nakshatra sub-lord divisions
 * ({@link VimshottariPartition#subDivisions()}) each split wherever it strictly
 * crosses a 30&deg; sign boundary, numbered 1–249 in increasing longitude order.
 *
 * <p>The count is exactly 249 (K. S. Krishnamurti's published horary table).
 * Boundaries are exact ({@link BigFraction}). Source: {@code core/REFERENCES.md}.
 */
public final class Horary249 {

    private static final BigFraction HALF = BigFraction.of(1, 2);
    private static final List<HoraryArc> ARCS = build();

    private Horary249() {}

    /** The 249 arcs, in number order (index 0 = arc 1). Immutable, cached. */
    public static List<HoraryArc> arcs() {
        return ARCS;
    }

    /** The arc for {@code number} (1..249). */
    public static HoraryArc arc(int number) {
        if (number < 1 || number > ARCS.size()) {
            throw new IllegalArgumentException("horary number out of 1.." + ARCS.size() + ": " + number);
        }
        return ARCS.get(number - 1);
    }

    public static int count() {
        return ARCS.size();
    }

    private record Piece(Graha subLord, BigFraction start, BigFraction end) {}

    private static List<HoraryArc> build() {
        List<BigFraction> signCusps = new ArrayList<>(11);
        for (int k = 1; k <= 11; k++) {
            signCusps.add(BigFraction.of(30L * k));
        }

        List<Piece> pieces = new ArrayList<>(260);
        for (Span span : VimshottariPartition.subDivisions()) {
            List<BigFraction> points = new ArrayList<>();
            points.add(span.start());
            for (BigFraction cusp : signCusps) {
                if (span.start().compareTo(cusp) < 0 && cusp.compareTo(span.end()) < 0) {
                    points.add(cusp);
                }
            }
            points.add(span.end());
            for (int i = 0; i < points.size() - 1; i++) {
                pieces.add(new Piece(span.lord(), points.get(i), points.get(i + 1)));
            }
        }
        pieces.sort(Comparator.comparing(Piece::start));

        List<HoraryArc> arcs = new ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            Piece p = pieces.get(i);
            double midpoint = p.start().add(p.end()).multiply(HALF).doubleValue();
            arcs.add(new HoraryArc(
                    i + 1, p.start(), p.end(), Sign.at(midpoint), KpLordage.chainFor(midpoint)));
        }
        return List.copyOf(arcs);
    }
}
