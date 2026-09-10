package com.celestia.geo.place;

import java.util.List;

/**
 * The result of geocoding one query: the query and its ranked candidate list
 * (possibly empty). Value equality — two results with equal query and equal
 * candidate list are equal (backs "an equal candidate list on a cache hit",
 * SC-003).
 *
 * @param query the query that produced this
 * @param candidates ranked ({@link CandidateRanking}); defensively copied
 */
public record GeocodeResult(PlaceQuery query, List<PlaceCandidate> candidates) {

    public GeocodeResult {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public boolean isEmpty() {
        return candidates.isEmpty();
    }
}
