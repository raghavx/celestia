package com.celestia.guardrail;

import com.celestia.core.toolkit.KpToolkit;
import com.celestia.core.toolkit.Sensitivity;
import com.celestia.core.toolkit.ToolkitSheet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The deterministic pass every outbound reply goes through (FR-013 — FR-018).
 *
 * <h2>Composition order, fixed so the result is deterministic</h2>
 *
 * <ol>
 *   <li>the out-of-scope marker → return the fixed redirect, <b>stop</b> (FR-016);
 *   <li>the leak scan → if anything fires, return the fixed safe message,
 *       <b>stop</b> (FR-017, FR-018);
 *   <li>prepend the support line when the signal is {@code INTENT} (FR-015);
 *   <li>append {@code sensitivityUsed.note()} when it is not {@code NONE},
 *       exactly once (FR-014).
 * </ol>
 *
 * <p>Steps 1 and 2 are <b>terminal</b>: a withheld reply is never decorated,
 * because decorating it would mean assembling a message around content already
 * judged undeliverable. The same order is also the refusal path — product copy
 * carries no marker, no secret and no sensitivity, so the only decoration a
 * refusal can receive is step 3's support line.
 */
public final class OutputStage {

    /**
     * What SPEC-012's hardened prompt instructs the model to emit when it has no
     * KP basis for a question. Nothing in this module produces it; the stage only
     * recognises it (FR-016).
     */
    static final String OUT_OF_SCOPE_MARKER = "<<OUT_OF_SCOPE>>";

    private static final String PROSE_CLASSES_RESOURCE = "com/celestia/guardrail/title-prose-classes.tsv";

    /** Shortest caller-supplied secret worth scanning for. Below this, false positives dominate. */
    private static final int MIN_SECRET_LENGTH = 8;

    /** A digit run this long is identifier-shaped; a horary number (1-249) and a year are not. */
    private static final int MIN_IDENTIFIER_DIGITS = 10;

    private final Pattern sheetIds;
    private final Pattern nonProseTitles;
    private final Pattern citationFramedTitles;
    private final List<Pattern> rowReferences;
    private final List<Pattern> reasoningMarkers;
    private final Pattern userIdToken;
    private final Map<LeakRule.ProseClass, List<String>> titlesByClass;

    private OutputStage(
            Pattern sheetIds,
            Pattern nonProseTitles,
            Pattern citationFramedTitles,
            Map<LeakRule.ProseClass, List<String>> titlesByClass) {
        this.sheetIds = sheetIds;
        this.nonProseTitles = nonProseTitles;
        this.citationFramedTitles = citationFramedTitles;
        this.titlesByClass = Map.copyOf(titlesByClass);
        this.rowReferences = List.of(
                Pattern.compile("\\brow\\s*#?\\s*\\d{1,5}\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\br\\d{3,5}\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("[a-z0-9][a-z0-9-]{3,}\\s*:\\s*r\\d+", Pattern.CASE_INSENSITIVE));
        this.reasoningMarkers = List.of(
                Pattern.compile("</?(thinking|thought|scratchpad|reasoning|internal)>", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(?m)^\\s*(system|assistant|user)\\s*:", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\bi (should|will|must|need to) call the \\w+ (tool|function)\\b",
                        Pattern.CASE_INSENSITIVE),
                Pattern.compile("<\\|im_(start|end)\\|>", Pattern.CASE_INSENSITIVE));
        this.userIdToken = Pattern.compile(
                "\\b(user ?id|userid|user_id|account id|customer id)\\b", Pattern.CASE_INSENSITIVE);
    }

    /**
     * The shared stage, built once from the bundled toolkit.
     *
     * @throws IllegalStateException if the toolkit cannot be loaded. The leak
     *     scan keys on its sheet ids and titles, and a scan with no vocabulary
     *     would deliver the very disclosures this stage exists to catch —
     *     symmetric with {@link Gate#bundled()}: both halves of the path fail
     *     closed (FR-017)
     */
    public static OutputStage bundled() {
        KpToolkit toolkit;
        try {
            toolkit = KpToolkit.bundled();
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "the output stage cannot be built without the toolkit: its leak scan keys on the "
                            + "toolkit's own ids and titles, and a scan with no vocabulary delivers "
                            + "everything it exists to catch",
                    e);
        }
        return from(toolkit);
    }

    /** Build a stage over a specific toolkit. */
    static OutputStage from(KpToolkit toolkit) {
        Map<String, LeakRule.ProseClass> overrides = loadOverrides();

        List<String> nonProseIds = new ArrayList<>();
        List<String> framedOnly = new ArrayList<>();
        Map<LeakRule.ProseClass, List<String>> byClass = new EnumMap<>(LeakRule.ProseClass.class);
        byClass.put(LeakRule.ProseClass.NON_PROSE, new ArrayList<>());
        byClass.put(LeakRule.ProseClass.PROSE_COLLIDING, new ArrayList<>());

        // All 75 sheets, housekeeping included: leaking "Version Control" is still a leak.
        for (ToolkitSheet sheet : toolkit.sheets()) {
            // Ids split by prose class too, and for the same reason as titles.
            // 16 of the 75 ids are single ordinary English words — numerology,
            // death, luck, property, relationship, romance — so keying every id
            // bare would block ordinary English. Research §8's claim that ids
            // "never appear in a legitimate answer" holds only for the slugs that
            // carry a hyphen or a digit (1500-rules, ready-reckoner,
            // horary-249-mindsets); those are keyed bare, the rest need a frame.
            if (classifyId(sheet.id()) == LeakRule.ProseClass.NON_PROSE) {
                nonProseIds.add(sheet.id());
            } else {
                framedOnly.add(sheet.id());
            }
            LeakRule.ProseClass klass = overrides.getOrDefault(sheet.title(), classify(sheet.title()));
            byClass.get(klass).add(sheet.title());
        }
        // The workbook's own title is non-prose by the same test.
        byClass.get(LeakRule.ProseClass.NON_PROSE).add("Prediction ToolKit for Advanced");
        byClass.get(LeakRule.ProseClass.NON_PROSE).add("Prediction ToolKit");

        framedOnly.addAll(byClass.get(LeakRule.ProseClass.PROSE_COLLIDING));
        return new OutputStage(
                alternation(nonProseIds),
                alternation(byClass.get(LeakRule.ProseClass.NON_PROSE)),
                citationFrame(framedOnly),
                byClass);
    }

    /**
     * The mechanical default (FR-017): a title is non-prose when any of its words
     * carries an internal capital, a digit or a {@code +}. A sheet added by a
     * later toolkit release is classified without anyone having to remember to.
     */
    static LeakRule.ProseClass classify(String title) {
        for (String word : title.split("\\s+")) {
            if (word.chars().anyMatch(c -> Character.isDigit(c) || c == '+')) {
                return LeakRule.ProseClass.NON_PROSE;
            }
            for (int i = 1; i < word.length(); i++) {
                if (Character.isUpperCase(word.charAt(i))) {
                    return LeakRule.ProseClass.NON_PROSE;
                }
            }
        }
        return LeakRule.ProseClass.PROSE_COLLIDING;
    }

    /**
     * The same principle as {@link #classify} applied to a slug: a hyphen or a
     * digit makes it unable to occur in ordinary English, so it is keyed bare.
     * A bare single-word slug ({@code numerology}, {@code death}) is ordinary
     * English and needs a citation frame like any prose-colliding title.
     */
    static LeakRule.ProseClass classifyId(String id) {
        boolean marked = id.chars().anyMatch(c -> c == '-' || c == '_' || Character.isDigit(c));
        return marked ? LeakRule.ProseClass.NON_PROSE : LeakRule.ProseClass.PROSE_COLLIDING;
    }

    /** Decorate a draft, or withhold it. */
    public OutputResult decorate(OutputRequest request) {
        // 1. The out-of-scope marker replaces the reply in full (FR-016). Terminal.
        if (request.draft().contains(OUT_OF_SCOPE_MARKER)) {
            return OutputResult.delivered(FixedMessages.refusalRedirect());
        }

        // 2. The leak scan. Terminal, and withholds in full rather than redacting
        //    — partial redaction leaves fragments of exactly the content that must
        //    not be sent, and tells the attacker their probe landed (FR-018).
        Optional<LeakRule.LeakFamily> leak = scan(request);
        if (leak.isPresent()) {
            return OutputResult.withheld(leak.get());
        }

        // 3. The support line, before any astrological content (FR-015).
        StringBuilder text = new StringBuilder();
        if (request.selfHarm() == SelfHarmSignal.INTENT) {
            text.append(FixedMessages.selfHarmSupportLine()).append("\n\n");
        }

        // 4. Exactly one advisory sentence, the product's own wording, at the end
        //    (FR-014). Any copy of it already in the draft is removed first, so
        //    "exactly one" holds however the draft was written.
        String body = request.draft();
        Sensitivity sensitivity = request.sensitivityUsed();
        if (sensitivity != Sensitivity.NONE) {
            String note = sensitivity.note();
            body = body.replace(note, "").stripTrailing();
            text.append(body);
            if (!body.isEmpty()) {
                text.append("\n\n");
            }
            text.append(note);
        } else {
            text.append(body);
        }
        return OutputResult.delivered(text.toString());
    }

    /**
     * The six signals of research.md §8, <b>none of which is a bare common word
     * or a bare number</b>.
     */
    private Optional<LeakRule.LeakFamily> scan(OutputRequest request) {
        String draft = request.draft();

        if (sheetIds.matcher(draft).find()) {
            return Optional.of(LeakRule.LeakFamily.SHEET_ID);
        }
        if (nonProseTitles.matcher(draft).find()) {
            return Optional.of(LeakRule.LeakFamily.SHEET_TITLE);
        }
        for (Pattern row : rowReferences) {
            if (row.matcher(draft).find()) {
                return Optional.of(LeakRule.LeakFamily.ROW_REFERENCE);
            }
        }
        if (citationFramedTitles != null && citationFramedTitles.matcher(draft).find()) {
            return Optional.of(LeakRule.LeakFamily.CITATION_FRAME);
        }
        for (String secret : request.secrets()) {
            if (secret != null && secret.length() >= MIN_SECRET_LENGTH
                    && draft.toLowerCase(Locale.ROOT).contains(secret.toLowerCase(Locale.ROOT))) {
                return Optional.of(LeakRule.LeakFamily.SECRET);
            }
        }
        for (Pattern marker : reasoningMarkers) {
            if (marker.matcher(draft).find()) {
                return Optional.of(LeakRule.LeakFamily.REASONING_MARKER);
            }
        }
        if (otherPartyIdentifier(draft, request.recipientRef())) {
            return Optional.of(LeakRule.LeakFamily.OTHER_PARTY_IDENTIFIER);
        }
        return Optional.empty();
    }

    /**
     * An identifier that is not the recipient's own (FR-017). Keyed on
     * identifier <b>shape</b> against a known-good reference, never on a bare
     * number — so "you were born in 1987" and "take horary number 108" stay
     * deliverable, as SPEC-010 FR-012 requires.
     */
    private boolean otherPartyIdentifier(String draft, String recipientRef) {
        if (userIdToken.matcher(draft).find()) {
            return true;
        }
        String recipientDigits = digitsOf(recipientRef);
        Matcher runs = Pattern.compile("\\+?[0-9][0-9\\s().-]{8,20}[0-9]").matcher(draft);
        while (runs.find()) {
            String digits = digitsOf(runs.group());
            if (digits.length() >= MIN_IDENTIFIER_DIGITS
                    && !(recipientDigits.contains(digits) || digits.contains(recipientDigits))) {
                return true;
            }
        }
        return false;
    }

    private static String digitsOf(String s) {
        StringBuilder out = new StringBuilder();
        s.chars().filter(Character::isDigit).forEach(c -> out.append((char) c));
        return out.toString();
    }

    /** Word-boundary alternation over literal phrases, case-insensitive. */
    private static Pattern alternation(List<String> literals) {
        String body = literals.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(Pattern::quote)
                .reduce((a, b) -> a + "|" + b)
                .orElse("(?!)");
        return Pattern.compile("\\b(?:" + body + ")\\b", Pattern.CASE_INSENSITIVE);
    }

    /**
     * A prose-colliding title fires only inside a citation frame — "per the …",
     * "according to the …", "source: …", "(… sheet)" — which is what separates
     * naming the source from using the word (research.md §8).
     */
    private static Pattern citationFrame(List<String> titles) {
        String body = titles.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(Pattern::quote)
                .reduce((a, b) -> a + "|" + b)
                .orElse("(?!)");
        String title = "(?:" + body + ")";
        return Pattern.compile(
                "(?:(?:per|as per|according to|from|see|refer to|source)\\s+(?:the\\s+)?" + title + ")"
                        + "|(?:source\\s*:\\s*" + title + ")"
                        + "|(?:" + title + "\\s+(?:sheet|table|tab|workbook))"
                        + "|(?:\\(\\s*" + title + "[^)]{0,20}(?:sheet|table|tab)\\s*\\))"
                        + "|(?:" + title + "\\s+(?:says|states|lists|gives))",
                Pattern.CASE_INSENSITIVE);
    }

    private static Map<String, LeakRule.ProseClass> loadOverrides() {
        Tsv.Table table = Tsv.read(PROSE_CLASSES_RESOURCE);
        int titleIndex = table.columnIndex("title");
        int classIndex = table.columnIndex("class");
        if (titleIndex < 0 || classIndex < 0) {
            throw new IllegalStateException(PROSE_CLASSES_RESOURCE + " needs 'title' and 'class' columns");
        }
        Map<String, LeakRule.ProseClass> overrides = new LinkedHashMap<>();
        for (List<String> record : table.records()) {
            String title = table.cell(record, titleIndex);
            String raw = table.cell(record, classIndex);
            if (title.isBlank()) {
                continue;
            }
            try {
                overrides.put(title, LeakRule.ProseClass.valueOf(raw));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "prose-class override for '" + title + "' names an unknown class: " + raw, e);
            }
        }
        return overrides;
    }

    /** The titles in one prose class. Diagnostic, and what {@code LeakRuleTest} asserts over. */
    List<String> titles(LeakRule.ProseClass klass) {
        return titlesByClass.getOrDefault(klass, List.of());
    }

    /** The overrides as loaded — so a stale entry can be detected against the manifest. */
    static Map<String, LeakRule.ProseClass> overrides() {
        return loadOverrides();
    }
}
