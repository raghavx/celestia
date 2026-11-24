package com.celestia.agent.llm.tools;

import com.celestia.agent.llm.SourceRef;
import com.celestia.agent.llm.SourceReferences;
import com.celestia.agent.llm.TurnScope;
import com.celestia.core.chart.NatalChart;
import com.celestia.core.lordage.Nakshatra;
import com.celestia.core.lordage.Sign;
import com.celestia.core.toolkit.HousePromise;
import com.celestia.core.toolkit.HoraryMindset;
import com.celestia.core.toolkit.KpToolkit;
import com.celestia.core.toolkit.PromiseEvaluator;
import com.celestia.core.toolkit.PromiseHit;
import com.celestia.core.toolkit.PromiseRule;
import com.celestia.core.toolkit.Sensitivity;
import com.celestia.core.toolkit.StarEntry;
import com.celestia.core.toolkit.ToolkitSheet;
import com.celestia.ephemeris.Graha;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * SPEC-010's five toolkit tools, as the model may reach them (SPEC-012 FR-007,
 * FR-013a, contracts/llm-tools.md).
 *
 * <p><b>Every result passes through {@link SourceReferences} before it is
 * serialised.</b> The sheet id, sheet title and row number are swapped for an
 * opaque reference — {@code s1}, {@code s2} — and the real values go to the turn
 * record. The model never receives a toolkit string, so Constitution X stops
 * resting on the prompt asking it not to repeat one and starts resting on the
 * wiring (FR-013a–d).
 *
 * <p><b>{@code lookupToolkit} needs the most care of the five.</b> SPEC-010's
 * shape returns the sheet's {@code id} and {@code title}, which is exactly what
 * must not reach the model, so this adapter returns the group, the kind and the
 * matching row text with the identity behind a reference. One careless tool would
 * defeat the swap in a single call.
 *
 * <p>A rule's own identifier is withheld for the same reason: {@code PromiseRule}
 * ids read {@code marriage-rules:r7}, which carries the sheet id inside it.
 *
 * <p>No method accepts a {@code userId}, a coordinate or a time — the chart comes
 * from {@link TurnScope} (FR-006).
 */
@Component
public class ToolkitTools {

    /** SPEC-010's caps, so a result cannot blow the reply budget or the context. */
    private static final int MAX_HITS = 40;
    private static final int MAX_SHEET_CHARS = 4000;
    private static final int MAX_SHEETS = 5;

    // The sheets behind the look-up tables. KpToolkit exposes the rows, not which
    // sheet they were imported from, so the mapping lives here — and goes through
    // sheet(id) so a title change in the workbook cannot drift from this file.
    private static final String SHEET_PROFESSIONS = "star-wise-professions";
    private static final String SHEET_DISEASES = "star-wise-diseases";
    private static final String SHEET_SIGNIFICATION = "planet-position-signification";
    private static final String SHEET_EDUCATION = "higher-education-streams";
    private static final String SHEET_PLANET_CHARACTERISTICS = "planet-characteristics";
    private static final String SHEET_PLANET_DIRECTION = "planet-direction";
    private static final String SHEET_ZODIAC_CHARACTERISTICS = "zodiac-signs-characteristics";
    private static final String SHEET_HORARY_MINDSETS = "horary-249-mindsets";

    private final KpToolkit toolkit;
    private final PromiseEvaluator evaluator;

    public ToolkitTools(KpToolkit toolkit, PromiseEvaluator evaluator) {
        this.toolkit = toolkit;
        this.evaluator = evaluator;
    }

    @Tool(description = "What the querent's chart promises, house by house: the cuspal sub lord, "
            + "its star lord, whether either is retrograde, the houses it connects, and the "
            + "matters indicated — each with how firmly the chart satisfies it. This is the "
            + "judgement; state nothing it does not support.")
    public List<HousePromiseView> getChartPromises(
            @ToolParam(required = false, description = "a single house 1..12; omit for all twelve") Integer house,
            @ToolParam(required = false, description = "include partly-indicated matters as well")
                    Boolean includePartial) {
        TurnScope scope = TurnScope.current();
        int before = scope.references().size();
        boolean partial = Boolean.TRUE.equals(includePartial);
        NatalChart chart = scope.context().chartToJudge();
        if (house != null) {
            scope.askedAboutHouse(house); // what the question is about (FR-014)
        }

        List<HousePromise> promises = house == null
                ? evaluator.evaluate(chart, scope.context().chart().significators(), partial)
                : List.of(evaluator.evaluateHouse(
                        chart, scope.context().chart().significators(), house, partial));

        List<HousePromiseView> views = new ArrayList<>(promises.size());
        int hits = 0;
        boolean truncated = false;
        for (HousePromise promise : promises) {
            List<MatterView> matters = new ArrayList<>();
            // Hits sharing a matterKey are reported once, with all their references —
            // SPEC-010 agent-tools.md obligation 3, which the reference swap must survive.
            Map<String, List<PromiseHit>> byMatter = new LinkedHashMap<>();
            for (PromiseHit hit : promise.hits()) {
                byMatter.computeIfAbsent(hit.rule().matterKey(), k -> new ArrayList<>()).add(hit);
            }
            for (List<PromiseHit> group : byMatter.values()) {
                if (hits >= MAX_HITS) {
                    truncated = true;
                    break;
                }
                hits += group.size();
                PromiseHit first = group.getFirst();
                List<String> refs = group.stream()
                        .map(h -> reference(scope, h.rule()))
                        .distinct()
                        .toList();
                Sensitivity worst = group.stream()
                        .map(h -> h.rule().sensitivity())
                        .reduce(Sensitivity.NONE, Sensitivity::max);
                // Attributed to the house it belongs to, not to the turn: a sweep of
                // all twelve must not put the 8th house's longevity caution on a
                // question about the 4th (SPEC-011 FR-014; TurnScope#sensitivityUsed).
                scope.sawSensitivity(promise.house(), worst);
                matters.add(new MatterView(
                        first.rule().matter(),
                        first.status().name(),
                        first.rule().houses(),
                        first.connected(),
                        first.missing(),
                        first.rule().hasCondition() ? first.rule().condition() : null,
                        worst.name(),
                        refs));
            }
            views.add(new HousePromiseView(
                    promise.house(),
                    promise.cuspSubLord().name(),
                    promise.cuspSubLordRetrograde(),
                    promise.starLord().name(),
                    promise.starLordRetrograde(),
                    promise.retrogradeCaveat(),
                    List.copyOf(promise.connectedHouses()),
                    matters));
        }
        scope.finishCall("getChartPromises", scope.references().referencesSince(before), truncated);
        return views;
    }

    @Tool(description = "Find the rules that speak to a matter — marriage, a job change, a "
            + "property purchase. Each names the house whose sub lord decides it and the houses "
            + "that must connect. Judge them with getChartPromises for that house.")
    public List<RuleView> findToolkitRules(
            @ToolParam(description = "the matter in the querent's own words") String matter) {
        TurnScope scope = TurnScope.current();
        int before = scope.references().size();
        List<PromiseRule> found = toolkit.findRules(matter);
        boolean truncated = found.size() > MAX_HITS;
        List<RuleView> views = found.stream()
                .limit(MAX_HITS)
                .map(rule -> {
                    scope.sawSensitivity(rule.judgedHouse(), rule.sensitivity());
                    return new RuleView(
                            rule.matter(),
                            rule.judgedHouse(),
                            rule.houses(),
                            rule.hasCondition() ? rule.condition() : null,
                            rule.sensitivity().name(),
                            reference(scope, rule));
                })
                .toList();
        scope.finishCall("findToolkitRules", scope.references().referencesSince(before), truncated);
        return views;
    }

    @Tool(description = "Search the knowledge base for a topic and get the matching text back. "
            + "Use it for methods and topics the rule tools do not cover.")
    public List<TopicView> lookupToolkit(
            @ToolParam(description = "the topic to look up") String topic) {
        TurnScope scope = TurnScope.current();
        int before = scope.references().size();
        List<KpToolkit.SheetHit> hits = toolkit.search(topic, MAX_SHEETS);
        List<TopicView> views = new ArrayList<>(hits.size());
        boolean truncated = false;
        int budget = MAX_SHEET_CHARS;
        for (KpToolkit.SheetHit hit : hits) {
            ToolkitSheet sheet = hit.sheet();
            if (!sheet.answerable()) {
                continue; // housekeeping and author-"ignore" sheets are never answer content
            }
            List<String> rows = new ArrayList<>();
            for (List<String> row : hit.matchingRows()) {
                String text = String.join(" · ", row).trim();
                if (text.isEmpty()) {
                    continue;
                }
                if (text.length() > budget) {
                    truncated = true;
                    break;
                }
                budget -= text.length();
                rows.add(text);
            }
            scope.sawSensitivity(sheet.sensitivity());
            // Group, kind and row text only. The id and the title stay behind the
            // reference: a tool that handed over sheet titles would defeat the swap.
            views.add(new TopicView(
                    sheet.group().name(),
                    sheet.kind().name(),
                    sheet.sensitivity().name(),
                    rows,
                    scope.references().reference(SourceRef.ofSheet(sheet.id(), sheet.title()))));
        }
        scope.finishCall("lookupToolkit", scope.references().referencesSince(before), truncated);
        return views;
    }

    @Tool(description = "One keyed row from a look-up table. Tables: profession, disease, "
            + "signification, education (these resolve through the querent's own chart); "
            + "planet-characteristics, direction, zodiac-characteristics (direct reference). "
            + "The point is a planet name, cusp:N, or a zodiac sign.")
    public LookupView getToolkitLookup(
            @ToolParam(description = "a planet name, cusp:N, or a zodiac sign name") String point,
            @ToolParam(description = "which table to read") String table) {
        TurnScope scope = TurnScope.current();
        int before = scope.references().size();
        LookupView view = lookup(scope, point, table == null ? "" : table.trim().toLowerCase(Locale.ROOT));
        scope.finishCall("getToolkitLookup", scope.references().referencesSince(before), false);
        return view;
    }

    @Tool(description = "The mindset reading for a horary number 1..249: its sign, star, the "
            + "sign, star and sub lords, and what that mindset indicates.")
    public MindsetView getHoraryMindset(
            @ToolParam(description = "the horary number the querent chose, 1 to 249") int number) {
        TurnScope scope = TurnScope.current();
        int before = scope.references().size();
        if (number < 1 || number > 249) {
            throw new IllegalArgumentException("a horary number must be between 1 and 249");
        }
        HoraryMindset mindset = toolkit.horary(number);
        MindsetView view = new MindsetView(
                mindset.number(),
                mindset.sign(),
                mindset.star(),
                mindset.signLord().name(),
                mindset.starLord().name(),
                mindset.subLord().name(),
                mindset.mindset(),
                scope.references().reference(sheetSource(SHEET_HORARY_MINDSETS, number)));
        scope.finishCall("getHoraryMindset", scope.references().referencesSince(before), false);
        return view;
    }

    // ------------------------------------------------------------------ lookups

    private LookupView lookup(TurnScope scope, String point, String table) {
        NatalChart chart = scope.context().chartToJudge();
        return switch (table) {
            case "profession" -> starLookup(scope, chart, point, true);
            case "disease" -> starLookup(scope, chart, point, false);
            case "signification" -> signification(scope, chart, point);
            case "education" -> education(scope, chart);
            case "planet-characteristics" -> direct(
                    scope, SHEET_PLANET_CHARACTERISTICS, toolkit.planetCharacteristics(graha(point)));
            case "direction" -> direct(scope, SHEET_PLANET_DIRECTION, toolkit.planetDirection(graha(point)));
            case "zodiac-characteristics" -> direct(
                    scope, SHEET_ZODIAC_CHARACTERISTICS, toolkit.zodiacCharacteristics(sign(point)));
            default -> new LookupView(false, "no such table: " + table, null);
        };
    }

    private LookupView starLookup(TurnScope scope, NatalChart chart, String point, boolean profession) {
        double longitude = longitudeOf(chart, point);
        Nakshatra nakshatra = Nakshatra.at(longitude);
        int quarter = Nakshatra.padaAt(longitude);
        Optional<StarEntry> entry = profession
                ? toolkit.starProfession(nakshatra, quarter)
                : toolkit.starDisease(nakshatra, quarter);
        String sheet = profession ? SHEET_PROFESSIONS : SHEET_DISEASES;
        scope.sawSensitivity(profession ? Sensitivity.NONE : Sensitivity.HEALTH);
        return entry
                .map(e -> new LookupView(
                        true,
                        e.label() + " — " + e.primary() + (e.secondary().isBlank() ? "" : " / " + e.secondary()),
                        scope.references().reference(sheetSource(sheet, 0))))
                .orElseGet(() -> new LookupView(false, "nothing recorded for that star quarter", null));
    }

    private LookupView signification(TurnScope scope, NatalChart chart, String point) {
        Graha graha = graha(point);
        int bhava = chart.placement(graha).bhava();
        return toolkit.planetInHouse(graha, bhava)
                .map(text -> new LookupView(
                        true, text, scope.references().reference(sheetSource(SHEET_SIGNIFICATION, bhava))))
                .orElseGet(() -> new LookupView(false, "nothing recorded for that placement", null));
    }

    private LookupView education(TurnScope scope, NatalChart chart) {
        Graha ninthCslStarLord = Nakshatra.at(
                        chart.position(chart.cuspSubLord(9)).longitude())
                .lord();
        int house = chart.placement(ninthCslStarLord).bhava();
        return toolkit.higherEducation(ninthCslStarLord, house)
                .map(text -> new LookupView(
                        true, text, scope.references().reference(sheetSource(SHEET_EDUCATION, house))))
                .orElseGet(() -> new LookupView(false, "nothing recorded for that combination", null));
    }

    private LookupView direct(TurnScope scope, String sheetId, String text) {
        return new LookupView(true, text, scope.references().reference(sheetSource(sheetId, 0)));
    }

    private double longitudeOf(NatalChart chart, String point) {
        String trimmed = point == null ? "" : point.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("cusp:")) {
            int house = Integer.parseInt(trimmed.substring("cusp:".length()).trim());
            if (house < 1 || house > 12) {
                throw new IllegalArgumentException("a house must be between 1 and 12");
            }
            return chart.position(chart.cuspSubLord(house)).longitude();
        }
        return chart.position(graha(trimmed)).longitude();
    }

    private static Graha graha(String name) {
        return Graha.valueOf(name.trim().toUpperCase(Locale.ROOT));
    }

    private static Sign sign(String name) {
        return Sign.valueOf(name.trim().toUpperCase(Locale.ROOT));
    }

    /** The reference for a rule — never the rule's own id, which carries the sheet id inside it. */
    private String reference(TurnScope scope, PromiseRule rule) {
        return scope.references().reference(sheetSource(rule.source(), rowOf(rule)));
    }

    private SourceRef sheetSource(String sheetId, int row) {
        String title = toolkit.sheet(sheetId).map(ToolkitSheet::title).orElse(sheetId);
        return new SourceRef(sheetId, title, Math.max(row, 0));
    }

    private static int rowOf(PromiseRule rule) {
        int marker = rule.id().lastIndexOf(":r");
        if (marker < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(rule.id().substring(marker + 2));
        } catch (NumberFormatException notARow) {
            return 0;
        }
    }

    // --------------------------------------------------------- model-visible views

    public record HousePromiseView(
            int house,
            String cuspSubLord,
            boolean cuspSubLordRetrograde,
            String starLord,
            boolean starLordRetrograde,
            boolean weakensOrDelays,
            List<Integer> connectedHouses,
            List<MatterView> matters) {}

    public record MatterView(
            String matter,
            String status,
            List<Integer> rulesHouses,
            List<Integer> connected,
            List<Integer> missing,
            String condition,
            String sensitivity,
            List<String> references) {}

    public record RuleView(
            String matter,
            int judgedHouse,
            List<Integer> houses,
            String condition,
            String sensitivity,
            String reference) {}

    public record TopicView(
            String group, String kind, String sensitivity, List<String> rows, String reference) {}

    public record LookupView(boolean found, String text, String reference) {}

    public record MindsetView(
            int number,
            String sign,
            String star,
            String signLord,
            String starLord,
            String subLord,
            String mindset,
            String reference) {}
}
