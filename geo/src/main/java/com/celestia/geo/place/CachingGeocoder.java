package com.celestia.geo.place;

import java.util.List;
import java.util.Optional;

/**
 * A {@link Geocoder} that serves repeat queries from a {@link GeocodeCache} and
 * answers coordinate queries directly.
 *
 * <ul>
 *   <li>A query that is a coordinate pair ({@link CoordinateQuery}) is answered
 *       from the parsed candidate — no delegate call, no cache write.</li>
 *   <li>A cache hit (keyed on {@link PlaceQuery#normalized()}) is returned
 *       unchanged — no delegate call.</li>
 *   <li>A miss calls the delegate, stores the result, and returns it.</li>
 * </ul>
 */
public final class CachingGeocoder implements Geocoder {

    private final Geocoder delegate;
    private final GeocodeCache cache;

    public CachingGeocoder(Geocoder delegate, GeocodeCache cache) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
        this.cache = java.util.Objects.requireNonNull(cache, "cache");
    }

    @Override
    public GeocodeResult geocode(PlaceQuery query) {
        Optional<PlaceCandidate> coord = CoordinateQuery.parse(query.raw());
        if (coord.isPresent()) {
            return new GeocodeResult(query, List.of(coord.get()));
        }

        String key = query.normalized();
        Optional<GeocodeResult> hit = cache.get(key);
        if (hit.isPresent()) {
            return hit.get();
        }

        GeocodeResult fresh = delegate.geocode(query);
        cache.put(key, fresh);
        return fresh;
    }
}
