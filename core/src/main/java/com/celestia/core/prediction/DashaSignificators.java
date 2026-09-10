package com.celestia.core.prediction;

import com.celestia.core.dasha.DashaLevel;
import com.celestia.core.dasha.DashaPeriod;
import com.celestia.core.dasha.DashaTimeline;
import com.celestia.core.dasha.RunningDasha;
import com.celestia.core.judgement.GrahaSignificators;
import com.celestia.core.judgement.SignificatorTable;
import com.celestia.ephemeris.Graha;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The dasha side of a daily reading: the running Vimshottari lords at the
 * reference instant, each lord's natal significations (SPEC-003), and the union
 * "activated" house set with per-house strength.
 * See {@code specs/006-daily-prediction/research.md} §2.
 *
 * @param running the five running lords at the reference instant
 * @param significationsByLord for each distinct running lord, its natal significations
 * @param activated the union, ordered by house
 * @param lordChangesWithinDay a running lord differs at {@code t ± 12 h}
 */
public record DashaSignificators(
        RunningDasha running,
        Map<Graha, GrahaSignificators> significationsByLord,
        List<ActivatedHouse> activated,
        boolean lordChangesWithinDay) {

    public DashaSignificators {
        significationsByLord = Map.copyOf(significationsByLord);
        activated = List.copyOf(activated);
    }

    /** The activation strength of {@code house}, or 0 if not activated. */
    public int strengthOf(int house) {
        return activated.stream()
                .filter(a -> a.house() == house)
                .mapToInt(ActivatedHouse::strength)
                .findFirst()
                .orElse(0);
    }

    public Set<Integer> activatedHouses() {
        Set<Integer> out = new LinkedHashSet<>();
        activated.forEach(a -> out.add(a.house()));
        return out;
    }

    public static DashaSignificators compute(
            SignificatorTable table, DashaTimeline timeline, Instant referenceInstant) {

        RunningDasha running = timeline.running(referenceInstant, 5);

        Set<Graha> lords = new LinkedHashSet<>();
        for (DashaPeriod p : running.stack()) {
            lords.add(p.lord());
        }

        Map<Graha, GrahaSignificators> byLord = new EnumMap<>(Graha.class);
        Map<Integer, Set<Graha>> houseToLords = new TreeMap<>();
        for (Graha lord : lords) {
            GrahaSignificators gs = table.grahaSignificators(lord);
            byLord.put(lord, gs);
            for (int house : gs.houses().keySet()) {
                houseToLords.computeIfAbsent(house, k -> EnumSet.noneOf(Graha.class)).add(lord);
            }
        }

        List<ActivatedHouse> activated = new ArrayList<>();
        houseToLords.forEach((house, signers) ->
                activated.add(new ActivatedHouse(house, signers.size(), signers)));

        boolean changes =
                !sameLords(running, timeline.running(referenceInstant.minus(12, ChronoUnit.HOURS), 5))
                        || !sameLords(running, timeline.running(referenceInstant.plus(12, ChronoUnit.HOURS), 5));

        return new DashaSignificators(running, byLord, activated, changes);
    }

    private static boolean sameLords(RunningDasha a, RunningDasha b) {
        for (DashaLevel level : DashaLevel.values()) {
            if (a.lord(level) != b.lord(level)) {
                return false;
            }
        }
        return true;
    }
}
