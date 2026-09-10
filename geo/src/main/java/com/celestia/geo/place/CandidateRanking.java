package com.celestia.geo.place;

import java.util.List;

/**
 * Orders a provider's candidates deterministically (research.md §6):
 *
 * <ol>
 *   <li>a candidate whose {@link QueryNormalizer#normalize normalised} display
 *       name equals the normalised query sorts <b>first</b>;</li>
 *   <li>otherwise the provider's own order is preserved — a <b>stable</b> sort;</li>
 *   <li>an exact tie on both keys falls back to the candidate {@code id}, so the
 *       order is total.</li>
 * </ol>
 *
 * <p>The list is never collapsed to one — disambiguation is the caller's
 * (SPEC-010). Run-to-run determinism for a given query comes from
 * {@link CachingGeocoder} (the provider is called once and the result frozen).
 */
public final class CandidateRanking {

    private CandidateRanking() {}

    public static List<PlaceCandidate> rank(PlaceQuery query, List<PlaceCandidate> raw) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        if (raw == null || raw.size() <= 1) {
            return raw == null ? List.of() : List.copyOf(raw);
        }

        String target = query.normalized();
        List<PlaceCandidate> exact = raw.stream()
                .filter(c -> QueryNormalizer.normalize(c.displayName()).equals(target))
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .toList();
        List<PlaceCandidate> rest = raw.stream()
                .filter(c -> !QueryNormalizer.normalize(c.displayName()).equals(target))
                .toList();

        return java.util.stream.Stream.concat(exact.stream(), rest.stream()).toList();
    }
}
