package com.celestia.geo.place;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises a query that is itself a coordinate pair — {@code "18.52, 73.85"},
 * {@code "-33.87 151.21"}, {@code "+40.7,-74.0"} — and turns it straight into a
 * candidate with no provider call, no normalisation, no cache (research.md §5,
 * FR-008).
 */
public final class CoordinateQuery {

    private CoordinateQuery() {}

    private static final Pattern PAIR = Pattern.compile(
            "^\\s*([+-]?\\d{1,3}(?:\\.\\d+)?)\\s*[,\\s]\\s*([+-]?\\d{1,3}(?:\\.\\d+)?)\\s*$");

    /** @return a {@code direct-input} candidate, or empty if {@code raw} is not a valid lat/lon pair */
    public static Optional<PlaceCandidate> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher m = PAIR.matcher(raw);
        if (!m.matches()) {
            return Optional.empty();
        }
        double lat = Double.parseDouble(m.group(1));
        double lon = Double.parseDouble(m.group(2));
        if (!(Math.abs(lat) <= 90.0) || !(Math.abs(lon) <= 180.0)) {
            return Optional.empty();
        }
        String label = String.format(Locale.ROOT, "%.5f, %.5f", lat, lon);
        String id = "coord:" + String.format(Locale.ROOT, "%.5f,%.5f", lat, lon);
        return Optional.of(new PlaceCandidate(id, label, "", "", lat, lon, "direct-input"));
    }
}
