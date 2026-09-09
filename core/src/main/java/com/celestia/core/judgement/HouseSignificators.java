package com.celestia.core.judgement;

import com.celestia.ephemeris.Graha;
import java.util.List;
import java.util.Set;

/**
 * The significators of one house — de-duplicated by graha, ordered by
 * {@link Significator#BY_STRENGTH}.
 *
 * @param house 1..12
 * @param significators immutable, ordered, one entry per graha
 */
public record HouseSignificators(int house, List<Significator> significators) {

    public HouseSignificators {
        if (house < 1 || house > 12) {
            throw new IllegalArgumentException("house out of 1..12: " + house);
        }
        long distinct = significators.stream().map(Significator::graha).distinct().count();
        if (distinct != significators.size()) {
            throw new IllegalArgumentException("duplicate graha in house " + house);
        }
        significators = List.copyOf(significators);
    }

    public boolean signifies(Graha graha) {
        return significators.stream().anyMatch(s -> s.graha() == graha);
    }

    /** The steps by which {@code graha} signifies this house, or an empty set. */
    public Set<Step> stepsFor(Graha graha) {
        return significators.stream()
                .filter(s -> s.graha() == graha)
                .findFirst()
                .map(Significator::steps)
                .orElse(Set.of());
    }
}
