package com.celestia.core.judgement;

import com.celestia.ephemeris.Graha;
import java.util.Map;
import java.util.Set;

/**
 * The houses one graha signifies — the transpose of the twelve
 * {@link HouseSignificators}.
 *
 * @param graha the graha
 * @param houses immutable {@code house -> steps} (only houses it signifies)
 */
public record GrahaSignificators(Graha graha, Map<Integer, Set<Step>> houses) {

    public GrahaSignificators {
        houses = Map.copyOf(houses);
    }

    public boolean signifies(int house) {
        return houses.containsKey(house);
    }

    /** The steps by which this graha signifies {@code house}, or an empty set. */
    public Set<Step> stepsFor(int house) {
        return houses.getOrDefault(house, Set.of());
    }
}
