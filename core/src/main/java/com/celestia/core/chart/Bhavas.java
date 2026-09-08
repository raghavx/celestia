package com.celestia.core.chart;

import com.celestia.core.lordage.Longitudes;
import com.celestia.core.lordage.Sign;
import java.util.List;

/**
 * House assignment.
 *
 * <ul>
 *   <li><b>Bhava</b> (KP judgement view): a graha is in bhava <i>n</i> iff its
 *       longitude lies in the forward arc {@code [cusp[n], cusp[n+1])} around the
 *       circle — half-open, boundary to the higher-longitude side, wrap-aware
 *       ({@code cusp[12] -> cusp[0]}). This is the cusp-to-cusp method (K. S.
 *       Krishnamurti, <i>KP Readers</i>), not the Sripati midpoint method.</li>
 *   <li><b>Rasi house</b> (display view): whole signs counted from the
 *       Ascendant's sign, which is house 1.</li>
 * </ul>
 *
 * See {@code core/REFERENCES.md}.
 */
public final class Bhavas {

    private Bhavas() {}

    /**
     * The bhava (1..12) containing {@code longitude}.
     *
     * @param cuspLongitudes the twelve cusp longitudes in house order (index 0 =
     *     house 1); a valid forward-monotone ring with exactly one wrap
     */
    public static int bhavaOf(double longitude, List<Double> cuspLongitudes) {
        if (cuspLongitudes.size() != 12) {
            throw new IllegalArgumentException("expected 12 cusps, got " + cuspLongitudes.size());
        }
        double lambda = Longitudes.normalize(longitude);
        for (int n = 0; n < 12; n++) {
            double cuspN = cuspLongitudes.get(n);
            double cuspNext = cuspLongitudes.get((n + 1) % 12);
            double arcToNext = Longitudes.normalize(cuspNext - cuspN);
            double arcToLambda = Longitudes.normalize(lambda - cuspN);
            if (arcToLambda < arcToNext) {
                return n + 1;
            }
        }
        // unreachable for a valid ring (the arcs sum to 360 and lambda is in [0,360))
        throw new IllegalStateException("no bhava for " + longitude + " in " + cuspLongitudes);
    }

    /** The rasi (sign-based) house (1..12) of a graha in {@code grahaSign}. */
    public static int rasiHouseOf(Sign grahaSign, Sign ascendantSign) {
        return 1 + Math.floorMod(grahaSign.ordinal() - ascendantSign.ordinal(), 12);
    }
}
