package com.celestia.core.toolkit;

import com.celestia.core.lordage.Nakshatra;
import com.celestia.core.lordage.Sign;
import com.celestia.ephemeris.Graha;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * The licensed KP toolkit, loaded from bundled resources — the product's single
 * source of KP interpretation (SPEC-010, Constitution X).
 *
 * <p>Immutable, deterministic, and read from the classpath only: no clock, no
 * network, no live file (Constitution II). The same toolkit release always
 * answers the same way, which is what makes a stored reading explainable
 * (FR-009).
 *
 * <h2>Traceability is internal</h2>
 *
 * Every rule and look-up carries the sheet it came from, so any interpretation
 * can be traced for logging, the eval harness and human review. That tracing is
 * <b>internal</b>: the toolkit is a licensed asset used to build Celestia, and a
 * reply to a user must never name it, a sheet's id or title, or a row number
 * (ADR-0025, FR-008). The agent speaks as the astrologer.
 *
 * <h2>Usage</h2>
 *
 * {@snippet lang=java :
 * KpToolkit toolkit = KpToolkit.bundled();
 * List<PromiseRule> marriage = toolkit.rulesFor(7);
 * HoraryMindset row = toolkit.horary(107);
 * }
 */
public final class KpToolkit {

    /** The classpath root {@link #bundled()} reads. */
    static final String PRODUCTION_ROOT = "com/celestia/core/toolkit";

    private static final int MIN_SEARCH_TERM = 3;
    private static final int TITLE_WEIGHT = 100;

    /**
     * The shared instance, built on first use.
     *
     * <p>Deliberately <em>not</em> the static-holder idiom. Loading can fail for
     * a legitimate, recoverable reason — the resources are generated from the
     * licensed workbook and are absent until it has been imported — and a failed
     * static initialiser would wrap that in {@code ExceptionInInitializerError}
     * and then poison the class with {@code NoClassDefFoundError} on every later
     * call. A caller that wants to degrade when the toolkit is absent could not
     * then catch it, and the contract promises {@link IllegalStateException}.
     */
    private static volatile KpToolkit bundled;

    private final ToolkitVersion version;
    private final List<ToolkitSheet> sheets;
    private final Map<String, ToolkitSheet> sheetsById;
    private final List<PromiseRule> rules;
    private final Map<Integer, List<PromiseRule>> rulesByHouse;
    private final List<HoraryMindset> horary;
    private final List<StarEntry> professions;
    private final List<StarEntry> diseases;
    private final Map<Graha, Map<Integer, String>> planetInHouse;
    private final Map<Graha, Map<Integer, String>> educationStreams;
    private final Map<Graha, Map<Graha, Friendship>> friendships;
    private final Map<Integer, String> houseMeanings;
    private final Map<Integer, String> relationships;
    private final Map<Graha, String> planetCharacteristics;
    private final Map<Graha, String> planetDirections;
    private final Map<Sign, String> zodiacCharacteristics;

    private KpToolkit(Loader loader) {
        this.version = loader.version;
        this.sheets = List.copyOf(loader.sheets);
        this.sheetsById = Map.copyOf(loader.sheetsById);
        this.rules = List.copyOf(loader.rules);
        this.rulesByHouse = Map.copyOf(loader.rulesByHouse);
        this.horary = List.copyOf(loader.horary);
        this.professions = List.copyOf(loader.professions);
        this.diseases = List.copyOf(loader.diseases);
        this.planetInHouse = Map.copyOf(loader.planetInHouse);
        this.educationStreams = Map.copyOf(loader.educationStreams);
        this.friendships = Map.copyOf(loader.friendships);
        this.houseMeanings = Map.copyOf(loader.houseMeanings);
        this.relationships = Map.copyOf(loader.relationships);
        this.planetCharacteristics = Map.copyOf(loader.planetCharacteristics);
        this.planetDirections = Map.copyOf(loader.planetDirections);
        this.zodiacCharacteristics = Map.copyOf(loader.zodiacCharacteristics);
    }

    /**
     * The shared toolkit, loaded once per JVM from the bundled resources.
     *
     * @throws IllegalStateException if the resources are missing or inconsistent
     *     (data-model.md "Validation at load")
     */
    public static KpToolkit bundled() {
        KpToolkit local = bundled;
        if (local == null) {
            synchronized (KpToolkit.class) {
                local = bundled;
                if (local == null) {
                    local = load(PRODUCTION_ROOT);
                    bundled = local;
                }
            }
        }
        return local;
    }

    /**
     * Load a toolkit from an arbitrary classpath root.
     *
     * <p>Package-private on purpose. Maven puts {@code src/test/resources} ahead
     * of {@code src/main/resources} on the test classpath, so a test fixture
     * placed at {@link #PRODUCTION_ROOT} would shadow the real resources and
     * {@link #bundled()} could never see them. Tests therefore load fixtures
     * from their own roots through this method, leaving {@code bundled()} to mean
     * "the real toolkit" — which is what makes the real-data assertions
     * meaningful.
     */
    static KpToolkit load(String classpathRoot) {
        return new KpToolkit(new Loader(classpathRoot).read());
    }

    // ---------------------------------------------------------------- release

    /** Which toolkit release these resources came from. */
    public ToolkitVersion version() {
        return version;
    }

    // ----------------------------------------------------------------- sheets

    /** Every sheet in workbook order, including the ones that are not answerable. */
    public List<ToolkitSheet> sheets() {
        return sheets;
    }

    /** The sheet with this id, if any. Finds non-answerable sheets too. */
    public Optional<ToolkitSheet> sheet(String id) {
        return Optional.ofNullable(sheetsById.get(id));
    }

    /** The sheets of one topic group, in workbook order. */
    public List<ToolkitSheet> sheets(TopicGroup group) {
        return sheets.stream().filter(s -> s.group() == group).toList();
    }

    /**
     * Answerable sheets matching every term of {@code query}, best first.
     *
     * <p>Ranked by title matches (weighted {@value #TITLE_WEIGHT}) plus the
     * number of matching rows, ties broken by workbook order. Terms shorter than
     * {@value #MIN_SEARCH_TERM} letters are ignored; an empty or all-short query
     * returns nothing rather than everything.
     *
     * <p>Housekeeping and author-"ignore" sheets never appear here (FR-014).
     * They remain reachable by {@link #sheet(String)} for inspection.
     */
    public List<SheetHit> search(String query, int limit) {
        if (query == null || limit <= 0) {
            return List.of();
        }
        List<String> terms = Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\W+"))
                .filter(t -> t.length() >= MIN_SEARCH_TERM)
                .toList();
        if (terms.isEmpty()) {
            return List.of();
        }

        List<SheetHit> hits = new ArrayList<>();
        for (ToolkitSheet sheet : sheets) {
            if (!sheet.answerable()) {
                continue;
            }
            String title = sheet.title().toLowerCase(Locale.ROOT);
            int score = 0;
            boolean everyTermFound = true;
            for (String term : terms) {
                boolean inTitle = title.contains(term);
                boolean inRows = sheet.containsTerm(term);
                if (inTitle) {
                    score += TITLE_WEIGHT;
                }
                if (!inTitle && !inRows) {
                    everyTermFound = false;
                    break;
                }
            }
            if (!everyTermFound) {
                continue;
            }
            List<List<String>> matching = sheet.rows().stream()
                    .filter(row -> terms.stream().anyMatch(term -> row.stream()
                            .anyMatch(cell -> cell.toLowerCase(Locale.ROOT).contains(term))))
                    .toList();
            score += matching.size();
            hits.add(new SheetHit(sheet, score, matching));
        }
        return hits.stream()
                .sorted(Comparator.comparingInt(SheetHit::score)
                        .reversed()
                        .thenComparingInt(h -> h.sheet().order()))
                .limit(limit)
                .toList();
    }

    /** A sheet matched by {@link #search}, with why it matched. */
    public record SheetHit(ToolkitSheet sheet, int score, List<List<String>> matchingRows) {
        public SheetHit {
            matchingRows = matchingRows.stream().map(List::copyOf).toList();
        }
    }

    // ------------------------------------------------------------ promise rules

    /** Every promise rule, in rule-book order. */
    public List<PromiseRule> promiseRules() {
        return rules;
    }

    /**
     * The rules judged from one house's cuspal sub lord.
     *
     * @throws IllegalArgumentException if {@code judgedHouse} is outside 1..12
     */
    public List<PromiseRule> rulesFor(int judgedHouse) {
        requireHouse(judgedHouse);
        return rulesByHouse.getOrDefault(judgedHouse, List.of());
    }

    /** Rules whose matter or category contains every term of {@code query}. */
    public List<PromiseRule> findRules(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> terms = Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\W+"))
                .filter(t -> !t.isBlank())
                .toList();
        if (terms.isEmpty()) {
            return List.of();
        }
        return rules.stream()
                .filter(rule -> {
                    String haystack =
                            (rule.matter() + " " + rule.category()).toLowerCase(Locale.ROOT);
                    return terms.stream().allMatch(haystack::contains);
                })
                .toList();
    }

    // ----------------------------------------------------------------- horary

    /**
     * The toolkit's row for a horary number, with its mindset.
     *
     * @throws IllegalArgumentException if {@code n} is outside 1..249
     */
    public HoraryMindset horary(int n) {
        if (n < 1 || n > horary.size()) {
            throw new IllegalArgumentException("horary number out of 1..249: " + n);
        }
        return horary.get(n - 1);
    }

    /** All 249 rows, in number order. */
    public List<HoraryMindset> horaryTable() {
        return horary;
    }

    // ---------------------------------------------------------------- look-ups

    /** The professions for a nakshatra-quarter, per the Star-wise Professions sheet. */
    public Optional<StarEntry> starProfession(Nakshatra nakshatra, int quarter) {
        return starEntry(professions, nakshatra, quarter);
    }

    /** The body parts and diseases for a nakshatra-quarter. */
    public Optional<StarEntry> starDisease(Nakshatra nakshatra, int quarter) {
        return starEntry(diseases, nakshatra, quarter);
    }

    /** What a graha in a bhava signifies. */
    public Optional<String> planetInHouse(Graha graha, int house) {
        requireHouse(house);
        return Optional.ofNullable(planetInHouse.getOrDefault(graha, Map.of()).get(house));
    }

    /**
     * The higher-education stream for the 9th cuspal sub lord's star lord, by the
     * house that star lord occupies.
     */
    public Optional<String> higherEducation(Graha ninthCslStarLord, int house) {
        requireHouse(house);
        return Optional.ofNullable(
                educationStreams.getOrDefault(ninthCslStarLord, Map.of()).get(house));
    }

    /**
     * How {@code of} regards {@code towards}. Not symmetric.
     *
     * @throws IllegalArgumentException if the two grahas are the same
     */
    public Friendship friendship(Graha of, Graha towards) {
        if (of == towards) {
            throw new IllegalArgumentException("friendship needs two different grahas, got " + of);
        }
        Friendship found = friendships.getOrDefault(of, Map.of()).get(towards);
        if (found == null) {
            throw new IllegalStateException("friendship table is missing " + of + " -> " + towards);
        }
        return found;
    }

    /** What a house signifies, per the Houses Meaning sheet. */
    public String houseMeaning(int house) {
        requireHouse(house);
        return houseMeanings.getOrDefault(house, "");
    }

    /** Which relation a cusp stands for when rotating the chart. */
    public String relationship(int cusp) {
        requireHouse(cusp);
        return relationships.getOrDefault(cusp, "");
    }

    /** A graha's characteristics. Keyed by the planet alone — no chart involved. */
    public String planetCharacteristics(Graha graha) {
        return planetCharacteristics.getOrDefault(graha, "");
    }

    /** The direction a graha stands for. Keyed by the planet alone. */
    public String planetDirection(Graha graha) {
        return planetDirections.getOrDefault(graha, "");
    }

    /** A sign's characteristics. Keyed by the sign alone — no chart involved. */
    public String zodiacCharacteristics(Sign sign) {
        return zodiacCharacteristics.getOrDefault(sign, "");
    }

    private static Optional<StarEntry> starEntry(List<StarEntry> table, Nakshatra nakshatra, int quarter) {
        if (quarter < 1 || quarter > 4) {
            throw new IllegalArgumentException("quarter out of 1..4: " + quarter);
        }
        return table.stream()
                .filter(e -> e.nakshatra() == nakshatra && e.covers(quarter))
                .findFirst();
    }

    private static void requireHouse(int house) {
        if (house < 1 || house > 12) {
            throw new IllegalArgumentException("house out of 1..12: " + house);
        }
    }

    // ------------------------------------------------------------------ loading

    /**
     * Reads one toolkit root and validates it before any query can see it.
     *
     * <p>Fails fast and loudly: a sheet that went missing between the manifest
     * and {@code toolkit.properties} would otherwise make the product quietly
     * believe it holds the whole toolkit while answering from part of it
     * (Constitution III and X).
     */
    private static final class Loader {

        private final String root;

        private ToolkitVersion version;
        private final List<ToolkitSheet> sheets = new ArrayList<>();
        private final Map<String, ToolkitSheet> sheetsById = new LinkedHashMap<>();
        private final List<PromiseRule> rules = new ArrayList<>();
        private final Map<Integer, List<PromiseRule>> rulesByHouse = new LinkedHashMap<>();
        private final List<HoraryMindset> horary = new ArrayList<>();
        private final List<StarEntry> professions = new ArrayList<>();
        private final List<StarEntry> diseases = new ArrayList<>();
        private final Map<Graha, Map<Integer, String>> planetInHouse = new EnumMap<>(Graha.class);
        private final Map<Graha, Map<Integer, String>> educationStreams = new EnumMap<>(Graha.class);
        private final Map<Graha, Map<Graha, Friendship>> friendships = new EnumMap<>(Graha.class);
        private final Map<Integer, String> houseMeanings = new LinkedHashMap<>();
        private final Map<Integer, String> relationships = new LinkedHashMap<>();
        private final Map<Graha, String> planetCharacteristics = new EnumMap<>(Graha.class);
        private final Map<Graha, String> planetDirections = new EnumMap<>(Graha.class);
        private final Map<Sign, String> zodiacCharacteristics = new HashMap<>();

        Loader(String root) {
            this.root = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        }

        Loader read() {
            readProperties();
            readSheets();
            readRules();
            readHorary();
            readStarTables();
            readPlanetHouseTables();
            readFriendship();
            readIndexedTables();
            readKeyedTables();
            validate();
            return this;
        }

        private void readProperties() {
            String path = root + "/toolkit.properties";
            Properties props = new Properties();
            try (InputStream in = KpToolkit.class.getClassLoader().getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException(
                            "toolkit resources not found: " + path
                                    + " — run tools/toolkit-import/import_toolkit.py against the licensed workbook");
                }
                props.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException("failed reading " + path, e);
            }
            version = new ToolkitVersion(
                    props.getProperty("title", ""),
                    props.getProperty("version", ""),
                    parseInt(props, "sheets"),
                    parseInt(props, "rules"),
                    props.getProperty("sourceSha256", ""));
        }

        private static int parseInt(Properties props, String key) {
            String raw = props.getProperty(key);
            if (raw == null) {
                throw new IllegalStateException("toolkit.properties is missing " + key);
            }
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("toolkit.properties " + key + " is not a number: " + raw, e);
            }
        }

        private void readSheets() {
            Tsv.Table manifest = Tsv.read(root + "/manifest.tsv");
            for (List<String> row : manifest.records()) {
                int order = Integer.parseInt(cell(row, 0));
                String id = cell(row, 1);
                List<List<String>> rows = sheetRows(id);
                ToolkitSheet sheet = new ToolkitSheet(
                        order,
                        id,
                        cell(row, 2),
                        TopicGroup.valueOf(cell(row, 3)),
                        SheetKind.valueOf(cell(row, 4)),
                        Sensitivity.valueOf(cell(row, 5)),
                        Boolean.parseBoolean(cell(row, 6)),
                        rows);
                sheets.add(sheet);
                if (sheetsById.put(id, sheet) != null) {
                    throw new IllegalStateException("duplicate sheet id in the manifest: " + id);
                }
            }
            sheets.sort(Comparator.comparingInt(ToolkitSheet::order));
        }

        private List<List<String>> sheetRows(String id) {
            String path = root + "/sheets/" + id + ".tsv";
            if (!Tsv.exists(path)) {
                throw new IllegalStateException("manifest lists sheet '" + id + "' but " + path + " is absent");
            }
            return Tsv.read(path).records();
        }

        private void readRules() {
            String path = root + "/rules/promise-rules.tsv";
            if (!Tsv.exists(path)) {
                return;
            }
            for (List<String> row : Tsv.read(path).records()) {
                PromiseRule rule = new PromiseRule(
                        cell(row, 0),
                        cell(row, 1),
                        cell(row, 2),
                        cell(row, 3),
                        cell(row, 4),
                        Integer.parseInt(cell(row, 5)),
                        parseHouses(cell(row, 6)),
                        cell(row, 7),
                        Sensitivity.valueOf(cell(row, 8)));
                rules.add(rule);
                rulesByHouse
                        .computeIfAbsent(rule.judgedHouse(), h -> new ArrayList<>())
                        .add(rule);
            }
            rulesByHouse.replaceAll((house, list) -> List.copyOf(list));
        }

        private static List<Integer> parseHouses(String raw) {
            if (raw.isBlank()) {
                return List.of();
            }
            List<Integer> houses = new ArrayList<>();
            for (String part : raw.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    houses.add(Integer.valueOf(trimmed));
                }
            }
            return houses;
        }

        private void readHorary() {
            String path = root + "/tables/horary-249.tsv";
            if (!Tsv.exists(path)) {
                return;
            }
            for (List<String> row : Tsv.read(path).records()) {
                horary.add(new HoraryMindset(
                        Integer.parseInt(cell(row, 0)),
                        cell(row, 1),
                        cell(row, 2),
                        cell(row, 3),
                        cell(row, 4),
                        Graha.valueOf(cell(row, 5)),
                        Graha.valueOf(cell(row, 6)),
                        Graha.valueOf(cell(row, 7)),
                        cell(row, 8)));
            }
            horary.sort(Comparator.comparingInt(HoraryMindset::number));
        }

        private void readStarTables() {
            readStarTable("star-professions", professions);
            readStarTable("star-diseases", diseases);
        }

        private void readStarTable(String name, List<StarEntry> into) {
            String path = root + "/tables/" + name + ".tsv";
            if (!Tsv.exists(path)) {
                return;
            }
            for (List<String> row : Tsv.read(path).records()) {
                Set<Integer> quarters = new TreeSet<>();
                for (String part : cell(row, 1).split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        quarters.add(Integer.valueOf(trimmed));
                    }
                }
                into.add(new StarEntry(
                        Nakshatra.valueOf(cell(row, 0)), quarters, cell(row, 2), cell(row, 3), cell(row, 4)));
            }
        }

        private void readPlanetHouseTables() {
            readPlanetHouseTable("planet-house-signification", planetInHouse);
            readPlanetHouseTable("higher-education-streams", educationStreams);
        }

        private void readPlanetHouseTable(String name, Map<Graha, Map<Integer, String>> into) {
            String path = root + "/tables/" + name + ".tsv";
            if (!Tsv.exists(path)) {
                return;
            }
            for (List<String> row : Tsv.read(path).records()) {
                into.computeIfAbsent(Graha.valueOf(cell(row, 0)), g -> new LinkedHashMap<>())
                        .put(Integer.parseInt(cell(row, 1)), cell(row, 2));
            }
            into.replaceAll((graha, byHouse) -> Map.copyOf(byHouse));
        }

        private void readFriendship() {
            String path = root + "/tables/planet-friendship.tsv";
            if (!Tsv.exists(path)) {
                return;
            }
            for (List<String> row : Tsv.read(path).records()) {
                friendships
                        .computeIfAbsent(Graha.valueOf(cell(row, 0)), g -> new EnumMap<>(Graha.class))
                        .put(Graha.valueOf(cell(row, 1)), Friendship.valueOf(cell(row, 2)));
            }
            friendships.replaceAll((graha, towards) -> Map.copyOf(towards));
        }

        private void readIndexedTables() {
            readIndexedTable("house-meanings", houseMeanings);
            readIndexedTable("relationship-rotation", relationships);
        }

        private void readIndexedTable(String name, Map<Integer, String> into) {
            String path = root + "/tables/" + name + ".tsv";
            if (!Tsv.exists(path)) {
                return;
            }
            for (List<String> row : Tsv.read(path).records()) {
                into.put(Integer.parseInt(cell(row, 0)), cell(row, 1));
            }
        }

        private void readKeyedTables() {
            readGrahaKeyed("planet-characteristics", planetCharacteristics);
            readGrahaKeyed("planet-direction", planetDirections);
            String path = root + "/tables/zodiac-characteristics.tsv";
            if (Tsv.exists(path)) {
                for (List<String> row : Tsv.read(path).records()) {
                    zodiacCharacteristics.put(Sign.valueOf(cell(row, 0)), cell(row, 1));
                }
            }
        }

        private void readGrahaKeyed(String name, Map<Graha, String> into) {
            String path = root + "/tables/" + name + ".tsv";
            if (!Tsv.exists(path)) {
                return;
            }
            for (List<String> row : Tsv.read(path).records()) {
                into.put(Graha.valueOf(cell(row, 0)), cell(row, 1));
            }
        }

        /** data-model.md "Validation at load" — every check fails the load. */
        private void validate() {
            if (sheets.size() != version.sheets()) {
                throw new IllegalStateException("toolkit.properties declares " + version.sheets()
                        + " sheets but manifest.tsv has " + sheets.size() + " rows");
            }
            if (rules.size() != version.rules()) {
                throw new IllegalStateException("toolkit.properties declares " + version.rules()
                        + " rules but promise-rules.tsv has " + rules.size() + " records");
            }
            for (PromiseRule rule : rules) {
                if (!sheetsById.containsKey(rule.source())) {
                    throw new IllegalStateException(
                            "rule " + rule.id() + " names source sheet '" + rule.source() + "', which is not in the manifest");
                }
            }
            if (!horary.isEmpty()) {
                if (horary.size() != 249) {
                    throw new IllegalStateException("the horary table must hold 249 rows, found " + horary.size());
                }
                for (int i = 0; i < 249; i++) {
                    if (horary.get(i).number() != i + 1) {
                        throw new IllegalStateException(
                                "horary rows must number 1..249 exactly once; row " + (i + 1) + " is "
                                        + horary.get(i).number());
                    }
                }
            }
            validateStarCoverage("star-professions", professions);
            validateStarCoverage("star-diseases", diseases);
            if (!friendships.isEmpty()) {
                int pairs = friendships.values().stream()
                        .mapToInt(Map::size)
                        .sum();
                if (pairs != 72) {
                    throw new IllegalStateException("the friendship table must hold all 72 ordered pairs, found " + pairs);
                }
            }
            validateKeyedCoverage("planet-characteristics", planetCharacteristics.keySet().size(), Graha.values().length);
            validateKeyedCoverage("planet-direction", planetDirections.keySet().size(), Graha.values().length);
            validateKeyedCoverage(
                    "zodiac-characteristics", zodiacCharacteristics.keySet().size(), Sign.values().length);
        }

        private static void validateStarCoverage(String name, List<StarEntry> table) {
            if (table.isEmpty()) {
                return;
            }
            for (Nakshatra nakshatra : Nakshatra.values()) {
                for (int quarter = 1; quarter <= 4; quarter++) {
                    int q = quarter;
                    long matches = table.stream()
                            .filter(e -> e.nakshatra() == nakshatra && e.covers(q))
                            .count();
                    if (matches != 1) {
                        throw new IllegalStateException(name + " must cover " + nakshatra + " quarter " + quarter
                                + " exactly once, found " + matches);
                    }
                }
            }
        }

        private static void validateKeyedCoverage(String name, int found, int expected) {
            if (found != 0 && found != expected) {
                throw new IllegalStateException(
                        name + " must cover all " + expected + " keys exactly once, found " + found);
            }
        }

        private static String cell(List<String> row, int index) {
            return index < row.size() ? row.get(index) : "";
        }
    }
}
