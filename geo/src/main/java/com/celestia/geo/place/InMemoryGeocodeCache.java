package com.celestia.geo.place;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A process-lifetime {@link GeocodeCache} with no expiry. Thread-safe.
 *
 * <p>Sufficient for a running service and for tests; a persistent, shareable
 * cache over the {@code geocode_cache} table is SPEC-008.
 */
public final class InMemoryGeocodeCache implements GeocodeCache {

    private final ConcurrentMap<String, GeocodeResult> byQueryNorm = new ConcurrentHashMap<>();

    @Override
    public Optional<GeocodeResult> get(String queryNorm) {
        return Optional.ofNullable(byQueryNorm.get(queryNorm));
    }

    @Override
    public void put(String queryNorm, GeocodeResult result) {
        if (queryNorm == null || queryNorm.isBlank()) {
            throw new IllegalArgumentException("queryNorm must not be blank");
        }
        byQueryNorm.put(queryNorm, result);
    }

    public int size() {
        return byQueryNorm.size();
    }
}
