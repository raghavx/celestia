package com.celestia.core.golden;

import com.celestia.ephemeris.Graha;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One parsed golden chart (see {@code core/src/test/resources/golden/README.md}).
 * Carries the SPEC-001 graha reference and the SPEC-002 cusp / angle reference.
 */
public record GoldenChart(
        String id,
        Instant instant,
        double latitude,
        double longitude,
        Map<Graha, Expected> expected,
        List<CuspRef> cusps,
        Map<String, LordChainRef> angles) {

    /** Reference values for one graha (SPEC-001 + SPEC-002 bhava / rasi house). */
    public record Expected(
            double longitude,
            String sign,
            String signLord,
            String nakshatra,
            int pada,
            String starLord,
            String subLord,
            String subSubLord,
            boolean retrograde,
            Integer bhava,
            Integer rasiHouse) {}

    /** A cusp's reference lord chain (SPEC-002). */
    public record CuspRef(int house, LordChainRef chain) {}

    /** longitude + lord chain, shared by cusps and angles. */
    public record LordChainRef(
            double longitude,
            String sign,
            String signLord,
            String nakshatra,
            int pada,
            String starLord,
            String subLord,
            String subSubLord) {}

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
            JsonNode birth = root.at("/birth");
            Instant instant = Instant.parse(birth.get("utc_instant").asText());
            double lat = birth.get("latitude").asDouble();
            double lon = birth.get("longitude").asDouble();

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
                        n.get("retrograde").asBoolean(),
                        optInt(n, "bhava"),
                        optInt(n, "rasi_house")));
            }

            List<CuspRef> cusps = new ArrayList<>();
            for (JsonNode c : root.at("/expected/cusps")) {
                cusps.add(new CuspRef(c.get("house").asInt(), chainRef(c)));
            }

            Map<String, LordChainRef> angles = new LinkedHashMap<>();
            JsonNode anglesNode = root.at("/expected/angles");
            for (String key : List.of("ascendant", "midheaven")) {
                JsonNode a = anglesNode.get(key);
                if (a != null && !a.isMissingNode()) {
                    angles.put(key, chainRef(a));
                }
            }

            return new GoldenChart(id, instant, lat, lon, expected, List.copyOf(cusps), Map.copyOf(angles));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static LordChainRef chainRef(JsonNode n) {
        return new LordChainRef(
                n.get("longitude").asDouble(),
                n.get("sign").asText(),
                n.get("sign_lord").asText(),
                n.get("nakshatra").asText(),
                n.get("pada").asInt(),
                n.get("star_lord").asText(),
                n.get("sub_lord").asText(),
                n.get("sub_sub_lord").asText());
    }

    private static Integer optInt(JsonNode parent, String field) {
        JsonNode n = parent.get(field);
        return (n == null || n.isNull()) ? null : n.asInt();
    }
}
