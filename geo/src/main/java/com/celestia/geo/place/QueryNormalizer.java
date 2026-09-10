package com.celestia.geo.place;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Normalises a free-text place query so the cache key is stable across spelling
 * variants and so historical / colloquial names resolve (research.md §5):
 *
 * <ol>
 *   <li>strip, then collapse every internal whitespace run to one space;</li>
 *   <li>lower-case with {@link Locale#ROOT};</li>
 *   <li>apply a small curated exonym → endonym map, whole-token.</li>
 * </ol>
 *
 * <p>{@link #normalize} is idempotent. The geocoder itself resolves most
 * historical names; {@link #aliases()} is the safety net for the ones a bare
 * provider query misses. <b>v1</b>, curated — extend by adding a row (documented
 * in {@code geo/REFERENCES.md}).
 */
public final class QueryNormalizer {

    private QueryNormalizer() {}

    /** Whole-token exonym → endonym replacements (research.md §5). */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("bombay", "mumbai"),
            Map.entry("calcutta", "kolkata"),
            Map.entry("madras", "chennai"),
            Map.entry("poona", "pune"),
            Map.entry("bangalore", "bengaluru"),
            Map.entry("baroda", "vadodara"),
            Map.entry("trivandrum", "thiruvananthapuram"),
            Map.entry("cochin", "kochi"),
            Map.entry("gauhati", "guwahati"),
            Map.entry("benares", "varanasi"),
            Map.entry("peking", "beijing"),
            Map.entry("saigon", "ho chi minh city"));

    public static String normalize(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        String collapsed = raw.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (collapsed.isEmpty()) {
            return collapsed;
        }
        // Whole-token alias substitution, keeping the rest of the phrase intact
        // ("bandra, bombay" -> "bandra, mumbai").
        return Arrays.stream(collapsed.split(" "))
                .map(token -> {
                    String core = token.replaceAll("[^\\p{L}\\p{N}]", "");
                    String alias = ALIASES.get(core);
                    if (alias == null) {
                        return token;
                    }
                    return token.replace(core, alias);
                })
                .collect(Collectors.joining(" "));
    }

    /** The curated alias map, for tests and documentation. */
    public static Map<String, String> aliases() {
        return ALIASES;
    }
}
