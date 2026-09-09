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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * One parsed golden chart (see {@code core/src/test/resources/golden/README.md}).
 * Carries the SPEC-001 graha reference, the SPEC-002 cusp / angle reference, and
 * the SPEC-003 significator / node-agency / ruling-planet reference.
 */
public record GoldenChart(
        String id,
        Instant instant,
        double latitude,
        double longitude,
        Map<Graha, Expected> expected,
        List<CuspRef> cusps,
        Map<String, LordChainRef> angles,
        Map<Integer, List<SigRef>> significatorsByHouse,
        Map<Graha, Map<Integer, Set<Integer>>> significatorsByGraha,
        Map<Graha, NodeAgencyRef> nodeAgency,
        RulingPlanetsRef rulingPlanets) {

    /** Reference values for one graha (SPEC-001 + SPEC-002 bhava / rasi house). */
    public record Expected(
            double longitude, String sign, String signLord, String nakshatra, int pada,
            String starLord, String subLord, String subSubLord, boolean retrograde,
            Integer bhava, Integer rasiHouse) {}

    public record CuspRef(int house, LordChainRef chain) {}

    public record LordChainRef(
            double longitude, String sign, String signLord, String nakshatra, int pada,
            String starLord, String subLord, String subSubLord) {}

    /** One significator: a graha and the KP steps (1..4) that qualified it. */
    public record SigRef(String graha, Set<Integer> steps) {}

    public record NodeAgencyRef(
            List<String> conjunctGrahas, String signLord, String starLord, List<String> agents) {}

    public record RulingPlanetsRef(
            Instant judgmentInstant, double latitude, double longitude,
            Instant sunriseUtc, String weekday, String dayLord,
            boolean dayLordFallback, boolean includeSubLords,
            Map<String, Set<String>> planetSources) {}

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
                        n.get("longitude").asDouble(), n.get("sign").asText(), n.get("sign_lord").asText(),
                        n.get("nakshatra").asText(), n.get("pada").asInt(), n.get("star_lord").asText(),
                        n.get("sub_lord").asText(), n.get("sub_sub_lord").asText(),
                        n.get("retrograde").asBoolean(), optInt(n, "bhava"), optInt(n, "rasi_house")));
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

            Map<Integer, List<SigRef>> byHouse = new TreeMap<>();
            JsonNode byHouseNode = root.at("/expected/significators/by_house");
            for (int h = 1; h <= byHouseNode.size(); h++) {
                List<SigRef> list = new ArrayList<>();
                for (JsonNode s : byHouseNode.get(h - 1)) {
                    Set<Integer> steps = new LinkedHashSet<>();
                    s.get("steps").forEach(step -> steps.add(step.asInt()));
                    list.add(new SigRef(s.get("graha").asText(), Set.copyOf(steps)));
                }
                byHouse.put(h, List.copyOf(list));
            }

            Map<Graha, Map<Integer, Set<Integer>>> byGraha = new EnumMap<>(Graha.class);
            JsonNode byGrahaNode = root.at("/expected/significators/by_graha");
            for (Graha g : Graha.values()) {
                Map<Integer, Set<Integer>> houses = new TreeMap<>();
                JsonNode entry = byGrahaNode.get(g.name());
                if (entry != null) {
                    entry.fields().forEachRemaining(e -> {
                        Set<Integer> steps = new LinkedHashSet<>();
                        e.getValue().forEach(step -> steps.add(step.asInt()));
                        houses.put(Integer.parseInt(e.getKey()), Set.copyOf(steps));
                    });
                }
                byGraha.put(g, Map.copyOf(houses));
            }

            Map<Graha, NodeAgencyRef> nodeAgency = new EnumMap<>(Graha.class);
            JsonNode naNode = root.at("/expected/node_agency");
            for (Graha node : List.of(Graha.RAHU, Graha.KETU)) {
                JsonNode a = naNode.get(node.name());
                if (a != null && !a.isMissingNode()) {
                    nodeAgency.put(node, new NodeAgencyRef(
                            strings(a.get("conjunct_grahas")), a.get("sign_lord").asText(),
                            a.get("star_lord").asText(), strings(a.get("agents"))));
                }
            }

            RulingPlanetsRef rp = null;
            JsonNode rpNode = root.at("/expected/ruling_planets");
            if (rpNode != null && !rpNode.isMissingNode()) {
                JsonNode j = rpNode.get("judgment");
                Map<String, Set<String>> ps = new LinkedHashMap<>();
                for (JsonNode pl : rpNode.get("planets")) {
                    ps.put(pl.get("graha").asText(), Set.copyOf(strings(pl.get("sources"))));
                }
                JsonNode sr = rpNode.get("sunrise_utc");
                rp = new RulingPlanetsRef(
                        Instant.parse(j.get("utc_instant").asText()),
                        j.get("latitude").asDouble(), j.get("longitude").asDouble(),
                        (sr == null || sr.isNull()) ? null : Instant.parse(sr.asText()),
                        rpNode.get("weekday").asText(), rpNode.get("day_lord").asText(),
                        rpNode.get("day_lord_fallback").asBoolean(),
                        rpNode.get("include_sub_lords").asBoolean(), Map.copyOf(ps));
            }

            return new GoldenChart(id, instant, lat, lon, expected, List.copyOf(cusps),
                    Map.copyOf(angles), Map.copyOf(byHouse), Map.copyOf(byGraha),
                    Map.copyOf(nodeAgency), rp);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static LordChainRef chainRef(JsonNode n) {
        return new LordChainRef(
                n.get("longitude").asDouble(), n.get("sign").asText(), n.get("sign_lord").asText(),
                n.get("nakshatra").asText(), n.get("pada").asInt(), n.get("star_lord").asText(),
                n.get("sub_lord").asText(), n.get("sub_sub_lord").asText());
    }

    private static List<String> strings(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null) {
            arr.forEach(n -> out.add(n.asText()));
        }
        return List.copyOf(out);
    }

    private static Integer optInt(JsonNode parent, String field) {
        JsonNode n = parent.get(field);
        return (n == null || n.isNull()) ? null : n.asInt();
    }
}
