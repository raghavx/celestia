package com.celestia.core.golden;

import com.celestia.ephemeris.Graha;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One parsed golden chart (see {@code core/src/test/resources/golden/README.md}).
 */
public record GoldenChart(String id, Instant instant, Map<Graha, Expected> expected) {

    /** The reference values for one graha. */
    public record Expected(
            double longitude,
            String sign,
            String signLord,
            String nakshatra,
            int pada,
            String starLord,
            String subLord,
            String subSubLord,
            boolean retrograde) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<GoldenChart> loadAll() {
        List<GoldenChart> charts = new ArrayList<>();
        for (String name : List.of("einstein-1879", "jobs-1955", "obama-1961")) {
            charts.add(load(name));
        }
        return charts;
    }

    public static GoldenChart load(String id) {
        String resource = "/golden/" + id + ".json";
        try (InputStream in = GoldenChart.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing golden resource " + resource);
            }
            JsonNode root = MAPPER.readTree(in);
            Instant instant = Instant.parse(root.at("/birth/utc_instant").asText());
            JsonNode grahas = root.at("/expected/grahas");

            Map<Graha, Expected> expected = new EnumMap<>(Graha.class);
            for (Graha g : Graha.values()) {
                JsonNode n = grahas.get(g.name());
                if (n == null || n.get("longitude") == null || n.get("longitude").isNull()) {
                    throw new IllegalStateException(id + ": expected block not populated for " + g
                            + " - run tools/ephe-crosscheck/compute_golden.py --write");
                }
                expected.put(g, new Expected(
                        n.get("longitude").asDouble(),
                        n.get("sign").asText(),
                        n.get("sign_lord").asText(),
                        n.get("nakshatra").asText(),
                        n.get("pada").asInt(),
                        n.get("star_lord").asText(),
                        n.get("sub_lord").asText(),
                        n.get("sub_sub_lord").asText(),
                        n.get("retrograde").asBoolean()));
            }
            return new GoldenChart(id, instant, expected);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
