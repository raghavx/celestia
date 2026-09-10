package com.celestia.core.prediction;

import com.celestia.ephemeris.Graha;
import java.util.Set;

/**
 * The v1 daily verdict for one matter, plus the raw material to reconstruct it by
 * hand (FR-017): the activated favourable / obstructive houses, the running lords
 * that activated them, and the transit bodies that triggered a favourable hit.
 */
public record MatterVerdict(
        Matter matter, Verdict verdict,
        Set<Integer> favourableHit, Set<Integer> obstructiveHit,
        Set<Graha> lords, Set<TransitBody> transits) {

    public MatterVerdict {
        favourableHit = Set.copyOf(favourableHit);
        obstructiveHit = Set.copyOf(obstructiveHit);
        lords = Set.copyOf(lords);
        transits = Set.copyOf(transits);
    }
}
