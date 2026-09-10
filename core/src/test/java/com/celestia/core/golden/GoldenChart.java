package com.celestia.core.golden;

import com.celestia.ephemeris.Graha;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
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
        RulingPlanetsRef rulingPlanets,
        DashaRef dasha,
        HoraryRef horary,
        DailyRef daily) {

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

    /** SPEC-004: the birth balance and the running five-lord stack at birth + 40 y. */
    public record DashaRef(
            String mahaLord, double elapsedFraction, double elapsedDays, double balanceDays,
            Instant mahaStart, Instant mahaEnd,
            Instant runningQueryUtc, List<String> runningLords, List<DashaPeriodRef> runningPeriods) {}

    public record DashaPeriodRef(String level, String lord, Instant start, Instant end) {}

    /** SPEC-005: one worked horary case (number + fixed judgment moment). */
    public record HoraryRef(
            int number, String subLord, Instant judgmentInstant, double latitude, double longitude,
            LordChainRef ascendant, LordChainRef midheaven, List<CuspRef> cusps,
            Map<Graha, HoraryPlacementRef> placements,
            Map<Integer, List<SigRef>> significatorsByHouse,
            Map<String, Set<String>> rulingPlanetSources) {}

    public record HoraryPlacementRef(double longitude, int bhava, int rasiHouse) {}

    /** SPEC-006: one worked daily-prediction reading. */
    public record DailyRef(
            LocalDate date, double longitude, Instant referenceInstant,
            List<String> runningLords,
            Map<Graha, Map<Integer, Set<Integer>>> significationsByLord,
            List<ActivatedRef> activated, boolean lordChangesWithinDay,
            String moonSubLord, String sunSubLord,
            Set<Integer> moonSupports, Set<Integer> sunSupports,
            boolean moonSubLordChangesWithinDay,
            Map<String, VerdictRef> verdicts) {}

    public record ActivatedRef(int house, int strength, List<String> lords) {}

    public record VerdictRef(
            String verdict, Set<Integer> favourableHit, Set<Integer> obstructiveHit,
            List<String> lords, List<String> transits) {}

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

            DashaRef dasha = null;
            JsonNode dNode = root.at("/expected/dasha");
            if (dNode != null && !dNode.isMissingNode()) {
                JsonNode bal = dNode.get("balance");
                JsonNode run = dNode.get("running");
                List<DashaPeriodRef> runPeriods = new ArrayList<>();
                for (JsonNode pn : run.get("periods")) {
                    runPeriods.add(new DashaPeriodRef(
                            pn.get("level").asText(), pn.get("lord").asText(),
                            Instant.parse(pn.get("start").asText()),
                            Instant.parse(pn.get("end").asText())));
                }
                dasha = new DashaRef(
                        bal.get("maha_lord").asText(), bal.get("elapsed_fraction").asDouble(),
                        bal.get("elapsed_days").asDouble(), bal.get("balance_days").asDouble(),
                        Instant.parse(bal.get("maha_start").asText()),
                        Instant.parse(bal.get("maha_end").asText()),
                        Instant.parse(run.get("query_utc").asText()),
                        strings(run.get("lords")), List.copyOf(runPeriods));
            }

            HoraryRef horary = null;
            JsonNode hNode = root.at("/expected/horary");
            if (hNode != null && !hNode.isMissingNode()) {
                JsonNode q = hNode.get("query");
                List<CuspRef> hCusps = new ArrayList<>();
                for (JsonNode c : hNode.get("cusps")) {
                    hCusps.add(new CuspRef(c.get("house").asInt(), chainRef(c)));
                }
                Map<Graha, HoraryPlacementRef> hPlace = new EnumMap<>(Graha.class);
                JsonNode plNode = hNode.get("placements");
                for (Graha g : Graha.values()) {
                    JsonNode pl = plNode.get(g.name());
                    hPlace.put(g, new HoraryPlacementRef(
                            pl.get("longitude").asDouble(), pl.get("bhava").asInt(),
                            pl.get("rasi_house").asInt()));
                }
                Map<Integer, List<SigRef>> hByHouse = new TreeMap<>();
                JsonNode hbh = hNode.at("/significators/by_house");
                for (int h = 1; h <= hbh.size(); h++) {
                    List<SigRef> list = new ArrayList<>();
                    for (JsonNode s : hbh.get(h - 1)) {
                        Set<Integer> steps = new LinkedHashSet<>();
                        s.get("steps").forEach(st -> steps.add(st.asInt()));
                        list.add(new SigRef(s.get("graha").asText(), Set.copyOf(steps)));
                    }
                    hByHouse.put(h, List.copyOf(list));
                }
                Map<String, Set<String>> hRp = new LinkedHashMap<>();
                for (JsonNode pl : hNode.at("/ruling_planets/planets")) {
                    hRp.put(pl.get("graha").asText(), Set.copyOf(strings(pl.get("sources"))));
                }
                horary = new HoraryRef(
                        q.get("number").asInt(), hNode.get("sub_lord").asText(),
                        Instant.parse(q.get("utc_instant").asText()),
                        q.get("latitude").asDouble(), q.get("longitude").asDouble(),
                        chainRef(hNode.get("ascendant")), chainRef(hNode.get("midheaven")),
                        List.copyOf(hCusps), Map.copyOf(hPlace), Map.copyOf(hByHouse),
                        Map.copyOf(hRp));
            }

            DailyRef daily = null;
            JsonNode dayNode = root.at("/expected/daily");
            if (dayNode != null && !dayNode.isMissingNode()) {
                JsonNode dz = dayNode.get("dasha");
                JsonNode tz = dayNode.get("transit");

                Map<Graha, Map<Integer, Set<Integer>>> byLord = new EnumMap<>(Graha.class);
                dz.get("significations_by_lord").fields().forEachRemaining(e -> {
                    Map<Integer, Set<Integer>> houses = new TreeMap<>();
                    e.getValue().fields().forEachRemaining(h -> {
                        Set<Integer> steps = new LinkedHashSet<>();
                        h.getValue().forEach(s -> steps.add(s.asInt()));
                        houses.put(Integer.parseInt(h.getKey()), Set.copyOf(steps));
                    });
                    byLord.put(Graha.valueOf(e.getKey()), Map.copyOf(houses));
                });

                List<ActivatedRef> activated = new ArrayList<>();
                for (JsonNode a : dz.get("activated")) {
                    activated.add(new ActivatedRef(
                            a.get("house").asInt(), a.get("strength").asInt(),
                            strings(a.get("lords"))));
                }

                Map<String, VerdictRef> verdicts = new LinkedHashMap<>();
                for (JsonNode v : dayNode.get("verdicts")) {
                    verdicts.put(v.get("matter").asText(), new VerdictRef(
                            v.get("verdict").asText(),
                            ints(v.get("favourable_hit")), ints(v.get("obstructive_hit")),
                            strings(v.get("lords")), strings(v.get("transits"))));
                }

                daily = new DailyRef(
                        LocalDate.parse(dayNode.get("date").asText()),
                        dayNode.get("longitude").asDouble(),
                        Instant.parse(dayNode.get("reference_instant").asText()),
                        strings(dz.get("running_lords")), Map.copyOf(byLord),
                        List.copyOf(activated), dz.get("lord_changes_within_day").asBoolean(),
                        tz.get("moon_sub_lord").asText(), tz.get("sun_sub_lord").asText(),
                        ints(tz.get("moon_supports")), ints(tz.get("sun_supports")),
                        tz.get("moon_sub_lord_changes_within_day").asBoolean(),
                        Map.copyOf(verdicts));
            }

            return new GoldenChart(id, instant, lat, lon, expected, List.copyOf(cusps),
                    Map.copyOf(angles), Map.copyOf(byHouse), Map.copyOf(byGraha),
                    Map.copyOf(nodeAgency), rp, dasha, horary, daily);
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

    private static Set<Integer> ints(JsonNode arr) {
        Set<Integer> out = new LinkedHashSet<>();
        if (arr != null) {
            arr.forEach(n -> out.add(n.asInt()));
        }
        return Set.copyOf(out);
    }

    private static Integer optInt(JsonNode parent, String field) {
        JsonNode n = parent.get(field);
        return (n == null || n.isNull()) ? null : n.asInt();
    }
}
