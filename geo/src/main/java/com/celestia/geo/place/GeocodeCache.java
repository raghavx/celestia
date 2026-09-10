package com.celestia.geo.place;

import java.util.Optional;

/**
 * A cache of geocode results, keyed on the <b>normalised</b> query
 * ({@link PlaceQuery#normalized()}).
 *
 * <p>{@code geo} ships {@link InMemoryGeocodeCache} (permanent for the process
 * lifetime, no TTL — a place's coordinates do not move; research.md §1). The
 * DB-backed implementation over {@code geocode_cache} is SPEC-008.
 */
public interface GeocodeCache {

    Optional<GeocodeResult> get(String queryNorm);

    void put(String queryNorm, GeocodeResult result);
}
