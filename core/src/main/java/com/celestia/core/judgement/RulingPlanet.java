package com.celestia.core.judgement;

import com.celestia.ephemeris.Graha;
import java.util.Set;

/**
 * One ruling planet and the source(s) that put it in the list.
 *
 * @param graha the ruling planet
 * @param sources non-empty, immutable
 */
public record RulingPlanet(Graha graha, Set<RpSource> sources) {

    public RulingPlanet {
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("a ruling planet must have at least one source");
        }
        sources = Set.copyOf(sources);
    }
}
