package com.celestia.geo.place;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-scope: parses {@code geo/src/test/resources/geocode/fixtures.json} into the
 * {@code Map<String, List<PlaceCandidate>>} a {@link FixtureGeocoder} takes.
 */
public final class FixtureGeocoderLoader {

    private FixtureGeocoderLoader() {}

    public static Map<String, List<PlaceCandidate>> load() {
        return load("/geocode/fixtures.json");
    }

    public static Map<String, List<PlaceCandidate>> load(String classpathResource) {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = FixtureGeocoderLoader.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("fixture resource not found: " + classpathResource);
            }
            JsonNode root = mapper.readTree(in);
            JsonNode queries = root.get("queries");
            Map<String, List<PlaceCandidate>> out = new LinkedHashMap<>();
            queries.fields().forEachRemaining(entry -> {
                List<PlaceCandidate> list = new ArrayList<>();
                for (JsonNode c : entry.getValue()) {
                    list.add(new PlaceCandidate(
                            c.get("id").asText(),
                            c.get("displayName").asText(),
                            c.path("country").asText(""),
                            c.path("adminRegion").asText(""),
                            c.get("latitude").asDouble(),
                            c.get("longitude").asDouble(),
                            c.get("source").asText()));
                }
                out.put(entry.getKey(), list);
            });
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static FixtureGeocoder geocoder() {
        return new FixtureGeocoder(load());
    }
}
