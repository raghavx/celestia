package com.celestia.core.prediction;

import com.celestia.ephemeris.Graha;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * The v1 daily-verdict rule — a total function of a matter's favourable /
 * obstructive house sets, the dasha-activated house set (with strengths), and the
 * transit-supported houses. See {@code core/REFERENCES.md} and
 * {@code specs/006-daily-prediction/research.md} §5.
 *
 * <pre>
 *   favActive  = { h ∈ favourable   : dasha-activated }   favStrength = Σ strength
 *   obsActive  = { h ∈ obstructive  : dasha-activated }   obsStrength = Σ strength
 *   favTriggered = favActive ∩ transit-supported
 *
 *   1. favActive and obsActive both empty        -> QUIET
 *   2. obsStrength > favStrength                  -> UNFAVOURABLE   (strict)
 *   3. favActive and favTriggered both non-empty  -> FAVOURABLE
 *   4. otherwise                                  -> MIXED
 * </pre>
 */
public final class VerdictRule {

    private VerdictRule() {}

    public static MatterVerdict evaluate(
            Matter matter, DashaSignificators dasha, TransitContribution transit) {

        Set<Integer> favActive = new TreeSet<>();
        for (int h : matter.favourable()) {
            if (dasha.strengthOf(h) > 0) {
                favActive.add(h);
            }
        }
        Set<Integer> obsActive = new TreeSet<>();
        for (int h : matter.obstructive()) {
            if (dasha.strengthOf(h) > 0) {
                obsActive.add(h);
            }
        }
        int favStrength = favActive.stream().mapToInt(dasha::strengthOf).sum();
        int obsStrength = obsActive.stream().mapToInt(dasha::strengthOf).sum();

        Set<Integer> favTriggered = new TreeSet<>(favActive);
        favTriggered.retainAll(transit.supported());

        Verdict verdict = classify(
                favActive.isEmpty(), obsActive.isEmpty(), favStrength, obsStrength,
                !favTriggered.isEmpty());

        Set<Integer> hit = new TreeSet<>(favActive);
        hit.addAll(obsActive);
        Set<Graha> lords = EnumSet.noneOf(Graha.class);
        for (ActivatedHouse a : dasha.activated()) {
            if (hit.contains(a.house())) {
                lords.addAll(a.lords());
            }
        }

        Set<TransitBody> transits = EnumSet.noneOf(TransitBody.class);
        if (!disjoint(favTriggered, transit.moonSupports())) {
            transits.add(TransitBody.MOON);
        }
        if (!disjoint(favTriggered, transit.sunSupports())) {
            transits.add(TransitBody.SUN);
        }

        return new MatterVerdict(matter, verdict, favActive, obsActive, lords, transits);
    }

    /**
     * The 4-row total decision (research.md §5), factored out for property testing.
     * First match wins; the four outcomes partition the input space.
     */
    static Verdict classify(
            boolean favActiveEmpty, boolean obsActiveEmpty,
            int favStrength, int obsStrength, boolean favTriggered) {
        if (favActiveEmpty && obsActiveEmpty) {
            return Verdict.QUIET;
        }
        if (obsStrength > favStrength) {
            return Verdict.UNFAVOURABLE;
        }
        if (!favActiveEmpty && favTriggered) {
            return Verdict.FAVOURABLE;
        }
        return Verdict.MIXED;
    }

    private static boolean disjoint(Set<Integer> a, Set<Integer> b) {
        return java.util.Collections.disjoint(a, b);
    }
}
