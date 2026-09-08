package com.celestia.core.lordage;

import com.celestia.core.dasha.Span;
import com.celestia.core.dasha.VimshottariPartition;
import com.celestia.ephemeris.Graha;
import java.util.List;

/**
 * Decomposes any ecliptic longitude into its KP lord chain (FR-006).
 *
 * <p>Every division is half-open {@code [start, end)} with the boundary going to
 * the higher division (FR-011); {@code chainFor(0.0)} is Aries / Ashwini / pada 1.
 * The input is normalised into {@code [0, 360)} first (FR-012).
 */
public final class KpLordage {

    private KpLordage() {}

    public static LordChain chainFor(double longitude) {
        double lambda = Longitudes.normalize(longitude);

        Sign sign = Sign.at(lambda);
        Nakshatra nakshatra = Nakshatra.at(lambda);
        int pada = Nakshatra.padaAt(lambda);
        Graha starLord = nakshatra.lord();

        Graha subLord = spanContaining(VimshottariPartition.subs(nakshatra), lambda).lord();
        Graha subSubLord =
                spanContaining(VimshottariPartition.subSubs(nakshatra, subLord), lambda).lord();

        return new LordChain(lambda, sign, sign.lord(), nakshatra, pada, starLord, subLord, subSubLord);
    }

    /** The half-open span containing {@code lambda}; the last span catches a top-boundary float. */
    private static Span spanContaining(List<Span> spans, double lambda) {
        for (Span s : spans) {
            if (s.contains(lambda)) {
                return s;
            }
        }
        return spans.get(spans.size() - 1);
    }
}
