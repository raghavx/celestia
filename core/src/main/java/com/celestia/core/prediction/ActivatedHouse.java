package com.celestia.core.prediction;

import com.celestia.ephemeris.Graha;
import java.util.Set;

/**
 * A natal house that ≥ 1 running Vimshottari lord signifies at the reference
 * instant.
 *
 * @param house 1..12
 * @param strength the number of distinct running lords that signify it, 1..5
 * @param lords those running lords (immutable)
 */
public record ActivatedHouse(int house, int strength, Set<Graha> lords) {

    public ActivatedHouse {
        if (house < 1 || house > 12) {
            throw new IllegalArgumentException("house out of 1..12: " + house);
        }
        lords = Set.copyOf(lords);
        if (strength != lords.size()) {
            throw new IllegalArgumentException(
                    "strength " + strength + " != lords.size() " + lords.size());
        }
        if (strength < 1 || strength > 5) {
            throw new IllegalArgumentException("strength out of 1..5: " + strength);
        }
    }
}
