package com.celestia.geo.place;

import java.util.List;
import java.util.Map;

/**
 * A {@link Geocoder} backed by an in-memory map of normalised query → candidates.
 * Pure, no I/O, no network — the binding {@code geo} ships for local development
 * and every test until the OpenCage HTTP adapter lands with its consumer
 * (SPEC-009/010, ADR-0013).
 *
 * <p>A test-scope loader reads {@code geo/src/test/resources/geocode/fixtures.json}
 * into the map.
 */
public final class FixtureGeocoder implements Geocoder {

    private final Map<String, List<PlaceCandidate>> byNormalizedQuery;

    public FixtureGeocoder(Map<String, List<PlaceCandidate>> byNormalizedQuery) {
        java.util.Objects.requireNonNull(byNormalizedQuery, "byNormalizedQuery");
        // Defensive deep-ish copy: keys as given, value lists frozen.
        this.byNormalizedQuery = byNormalizedQuery.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    }

    @Override
    public GeocodeResult geocode(PlaceQuery query) {
        List<PlaceCandidate> hits = byNormalizedQuery.getOrDefault(query.normalized(), List.of());
        return new GeocodeResult(query, CandidateRanking.rank(query, hits));
    }
}
