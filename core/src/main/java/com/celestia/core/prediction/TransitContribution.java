package com.celestia.core.prediction;

import com.celestia.core.judgement.SignificatorTable;
import com.celestia.core.lordage.KpLordage;
import com.celestia.core.lordage.LordChain;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The transit side of a daily reading: the KP lord chains of the transiting Moon
 * and Sun at the reference instant, and — by the v1 sub-lord rule — the natal
 * significator houses each supports (a body supports house H iff its sub lord is
 * a natal significator of H).
 * See {@code specs/006-daily-prediction/research.md} §3.
 */
public record TransitContribution(
        LordChain moonChain, LordChain sunChain,
        Set<Integer> moonSupports, Set<Integer> sunSupports,
        boolean moonSubLordChangesWithinDay) {

    public TransitContribution {
        moonSupports = Set.copyOf(moonSupports);
        sunSupports = Set.copyOf(sunSupports);
    }

    /** {@code moonSupports ∪ sunSupports}. */
    public Set<Integer> supported() {
        Set<Integer> out = new LinkedHashSet<>(moonSupports);
        out.addAll(sunSupports);
        return Set.copyOf(out);
    }

    public static TransitContribution compute(
            SignificatorTable table, double moonLongitude, double sunLongitude,
            double moonLongitudeTwelveHoursBefore, double moonLongitudeTwelveHoursAfter) {

        LordChain moon = KpLordage.chainFor(moonLongitude);
        LordChain sun = KpLordage.chainFor(sunLongitude);

        Set<Integer> moonSupports =
                Set.copyOf(table.grahaSignificators(moon.subLord()).houses().keySet());
        Set<Integer> sunSupports =
                Set.copyOf(table.grahaSignificators(sun.subLord()).houses().keySet());

        boolean changes =
                KpLordage.chainFor(moonLongitudeTwelveHoursBefore).subLord() != moon.subLord()
                        || KpLordage.chainFor(moonLongitudeTwelveHoursAfter).subLord() != moon.subLord();

        return new TransitContribution(moon, sun, moonSupports, sunSupports, changes);
    }
}
