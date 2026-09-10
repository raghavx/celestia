package com.celestia.geo.place;

/**
 * A place query: the raw text the user gave and its normalised form
 * ({@link QueryNormalizer}). The normalised form is the cache key.
 *
 * @param raw the text as supplied; non-blank
 * @param normalized {@code QueryNormalizer.normalize(raw)}; non-blank
 */
public record PlaceQuery(String raw, String normalized) {

    public PlaceQuery {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("raw query must not be blank");
        }
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("normalized query must not be blank (raw=\"" + raw + "\")");
        }
    }

    public static PlaceQuery of(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        return new PlaceQuery(raw, QueryNormalizer.normalize(raw));
    }
}
