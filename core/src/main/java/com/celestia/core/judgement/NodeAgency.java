package com.celestia.core.judgement;

import com.celestia.ephemeris.Graha;
import java.util.Set;

/**
 * How Rahu or Ketu signifies, by agency: the union of the houses signified by any
 * graha it is conjoined with (same bhava), the lord of the sign it occupies, and
 * the lord of the star it occupies. Source: {@code core/REFERENCES.md}.
 *
 * @param node {@link Graha#RAHU} or {@link Graha#KETU}
 * @param conjunctGrahas non-node grahas in the node's bhava
 * @param signLord lord of the sign the node occupies
 * @param starLord nakshatra lord of the node's longitude
 * @param agents {@code conjunctGrahas ∪ {signLord, starLord}}
 */
public record NodeAgency(
        Graha node, Set<Graha> conjunctGrahas, Graha signLord, Graha starLord, Set<Graha> agents) {

    public NodeAgency {
        if (node != Graha.RAHU && node != Graha.KETU) {
            throw new IllegalArgumentException("not a node: " + node);
        }
        conjunctGrahas = Set.copyOf(conjunctGrahas);
        agents = Set.copyOf(agents);
    }
}
