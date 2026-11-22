package com.celestia.core.toolkit;

import com.celestia.ephemeris.Graha;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * What one house of one chart promises, per the toolkit (SPEC-010 FR-004/005).
 *
 * <p>Computed, never stored by {@code core}: {@link PromiseEvaluator} derives it
 * from a {@code NatalChart} and keeps nothing, which is what lets a second
 * person's chart be judged for one reply and then forgotten (FR-012,
 * Constitution VII).
 *
 * @param house 1..12
 * @param cuspSubLord the cuspal sub lord — in KP, what decides the house's matters
 * @param cuspSubLordRetrograde whether that sub lord is retrograde
 * @param starLord the star lord of the cuspal sub lord, which delivers the result
 * @param starLordRetrograde whether that star lord is retrograde
 * @param connectedHouses the houses the sub lord reaches through its star lord,
 *     ascending (research.md &sect;3)
 * @param hits the matched rules: PROMISED and CONDITION_TO_CHECK in rule-book
 *     order, then PARTLY_INDICATED when those were asked for
 * @param sensitivity the most severe class among {@link #hits}
 */
public record HousePromise(
        int house,
        Graha cuspSubLord,
        boolean cuspSubLordRetrograde,
        Graha starLord,
        boolean starLordRetrograde,
        Set<Integer> connectedHouses,
        List<PromiseHit> hits,
        Sensitivity sensitivity) {

    public HousePromise {
        if (house < 1 || house > 12) {
            throw new IllegalArgumentException("house out of 1..12: " + house);
        }
        // Ascending and immutable. Set.copyOf would lose the order, and the
        // order is part of the contract: a reading lists connected houses the
        // way an astrologer writes them, 2-7-11 rather than 11-7-2.
        connectedHouses = Collections.unmodifiableSet(new TreeSet<>(connectedHouses));
        hits = List.copyOf(hits);
    }

    /**
     * Whether the reply must mention a weakening or delaying influence.
     *
     * <p>The toolkit's Prediction Algorithm treats a retrograde cuspal sub lord
     * or star lord as weakening or delaying the result, so an answer that draws
     * on this house has to say so (spec.md Edge Cases;
     * contracts/agent-tools.md obligation 4).
     */
    public boolean retrogradeCaveat() {
        return cuspSubLordRetrograde || starLordRetrograde;
    }

    /** The hits with the given status, in order. */
    public List<PromiseHit> withStatus(PromiseHit.Status status) {
        return hits.stream().filter(h -> h.status() == status).toList();
    }

    /** True when the toolkit promises nothing for this house in this chart. */
    public boolean empty() {
        return hits.isEmpty();
    }
}
