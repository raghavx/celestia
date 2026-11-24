package com.celestia.guardrail;

import com.celestia.core.lordage.Nakshatra;
import com.celestia.core.lordage.Sign;
import com.celestia.core.toolkit.KpToolkit;
import com.celestia.core.toolkit.PromiseRule;
import com.celestia.core.toolkit.ToolkitSheet;
import com.celestia.core.toolkit.TopicGroup;
import com.celestia.ephemeris.Graha;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The evidence that a message belongs to a toolkit topic group with answerable
 * content (FR-006, FR-007, research.md §4 as amended by §12).
 *
 * <p><b>Derived, not authored.</b> Three sources, only the third hand-written:
 *
 * <ol>
 *   <li><b>KP vocabulary</b> — the engine's own {@link Nakshatra}, {@link Graha}
 *       and {@link Sign} constants, house ordinals, and the KP terms that run
 *       through the whole toolkit ("sub lord", "dasha", "horary").
 *   <li><b>Toolkit matter vocabulary</b> — every answerable sheet's title and
 *       its promise-rule matters, minus the stopwords. This is what makes FR-006
 *       literally true: widening the toolkit widens the gate, with no list to
 *       maintain.
 *   <li><b>A small English supplement</b> — the compounds and synonyms the
 *       toolkit's wording misses ("gemstone" where it writes "gems").
 * </ol>
 *
 * <p><b>Group attribution is weak by construction.</b> A term takes the group of
 * its source sheet, <em>except</em> terms from the master rule book
 * {@code 1500-rules}, which holds 1,598 of the 2,001 rules and spans every
 * subject. Those terms are evidence that the message is astrology at all and
 * carry no subject claim. The group is therefore a best-effort diagnostic label,
 * never a result (research.md §12 finding 1, §13).
 *
 * <p><b>One matched term is enough</b> (research.md §5). The terms that survive
 * the stopword filter are subject matter, not filler; requiring two would refuse
 * "marriage?" — one word, and that word is the entire question.
 *
 * <p><b>Word boundaries, never bare substrings</b> (§12 finding 4). Matching
 * tokenises the message and looks up n-grams, so {@code ank} cannot match inside
 * {@code bank} nor {@code rog} inside {@code progress}.
 */
final class InScopeVocabulary {

    private static final String STOPWORDS_RESOURCE = "com/celestia/guardrail/stopwords.tsv";
    private static final String SUPPLEMENT_RESOURCE = "com/celestia/guardrail/topic-terms-en.tsv";

    /**
     * The master rule book. Its vocabulary spans every subject, so its terms
     * admit a message without claiming a subject (research.md §12 finding 1).
     */
    private static final String MASTER_RULE_BOOK = "1500-rules";

    /** Shortest term worth indexing. Below this, terms are noise rather than subject matter. */
    private static final int MIN_TERM_LENGTH = 3;

    /** Longest phrase the matcher will look up, in words. */
    private static final int MAX_TERM_WORDS = 4;

    /** The KP terms that appear throughout the toolkit rather than on one sheet. */
    private static final List<String> KP_TERMS = List.of(
            "sub lord", "sublord", "sub sub lord", "star lord", "starlord", "sign lord", "cusp", "cuspal",
            "dasha", "dasa", "bhukti", "antara", "mahadasha", "vimshottari",
            "horary", "prashna", "kundli", "kundali", "horoscope", "chart", "birth chart", "natal",
            "ruling planet", "ruling planets", "significator", "significators", "nakshatra", "nakshatras",
            "retrograde", "combust", "aspect", "ascendant", "lagna", "rasi", "rashi", "navamsa",
            "maraka", "badhaka", "yogakaraka", "transit", "gochara", "muhurta", "muhurat",
            "planet", "planets", "star", "house", "houses", "lord", "lords", "zodiac");

    private final Map<String, Term> byTerm;
    private final int maxWords;

    /**
     * One piece of in-scope evidence.
     *
     * @param term the normalised term
     * @param group the group of its source sheet, or empty when it carries no
     *     subject claim (master rule book, KP vocabulary)
     * @param source where it came from
     */
    record Term(String term, Optional<TopicGroup> group, Source source) {}

    /** Where a term came from. Only {@link #SUPPLEMENT} is hand-written. */
    enum Source {
        KP_VOCABULARY,
        TOOLKIT_MATTER,
        SUPPLEMENT
    }

    private InScopeVocabulary(Map<String, Term> byTerm) {
        this.byTerm = Map.copyOf(byTerm);
        this.maxWords = byTerm.keySet().stream()
                .mapToInt(t -> t.split(" ").length)
                .max()
                .orElse(1);
    }

    /** Build the vocabulary once from the bundled toolkit. */
    static InScopeVocabulary from(KpToolkit toolkit) {
        Set<String> stopwords = loadStopwords();
        Map<String, Term> terms = new HashMap<>();

        // 1. KP vocabulary — from the engine's own constants, so it cannot drift.
        for (Nakshatra nakshatra : Nakshatra.values()) {
            put(terms, readable(nakshatra.name()), Optional.empty(), Source.KP_VOCABULARY, stopwords);
        }
        for (Graha graha : Graha.values()) {
            put(terms, readable(graha.name()), Optional.empty(), Source.KP_VOCABULARY, stopwords);
        }
        for (Sign sign : Sign.values()) {
            put(terms, readable(sign.name()), Optional.empty(), Source.KP_VOCABULARY, stopwords);
        }
        for (String term : KP_TERMS) {
            put(terms, term, Optional.empty(), Source.KP_VOCABULARY, stopwords);
        }
        for (int house = 1; house <= 12; house++) {
            put(terms, ordinal(house) + " house", Optional.empty(), Source.KP_VOCABULARY, stopwords);
            put(terms, "house " + house, Optional.empty(), Source.KP_VOCABULARY, stopwords);
        }

        // 2. Toolkit matter vocabulary — titles and promise matters of answerable
        //    sheets only, so housekeeping and author-"ignore" content cannot make
        //    a message in scope (FR-007).
        Map<String, ToolkitSheet> sheets = new HashMap<>();
        for (ToolkitSheet sheet : toolkit.sheets()) {
            sheets.put(sheet.id(), sheet);
            if (!sheet.answerable()) {
                continue;
            }
            Optional<TopicGroup> group = subjectClaim(sheet.id(), sheet.group());
            addPhraseAndWords(terms, sheet.title(), group, Source.TOOLKIT_MATTER, stopwords);
        }
        for (PromiseRule rule : toolkit.promiseRules()) {
            ToolkitSheet sheet = sheets.get(rule.source());
            if (sheet == null || !sheet.answerable()) {
                continue;
            }
            Optional<TopicGroup> group = subjectClaim(rule.source(), sheet.group());
            addPhraseAndWords(terms, rule.matter(), group, Source.TOOLKIT_MATTER, stopwords);
        }

        // 3. The hand-written supplement.
        loadSupplement(terms, stopwords);

        if (terms.isEmpty()) {
            throw new IllegalStateException(
                    "no in-scope vocabulary could be derived — the gate would refuse every question");
        }
        return new InScopeVocabulary(terms);
    }

    /**
     * The group a sheet's terms may claim: none for the master rule book, whose
     * vocabulary spans every subject (research.md §12 finding 1).
     */
    private static Optional<TopicGroup> subjectClaim(String sheetId, TopicGroup group) {
        return MASTER_RULE_BOOK.equals(sheetId) ? Optional.empty() : Optional.ofNullable(group);
    }

    /** Index the whole phrase and each of its words. */
    private static void addPhraseAndWords(
            Map<String, Term> terms, String raw, Optional<TopicGroup> group, Source source, Set<String> stopwords) {
        String phrase = normaliseTerm(raw);
        if (phrase.isBlank()) {
            return;
        }
        String[] words = phrase.split(" ");
        if (words.length > 1 && words.length <= MAX_TERM_WORDS) {
            put(terms, phrase, group, source, stopwords);
        }
        for (String word : words) {
            put(terms, word, group, source, stopwords);
        }
    }

    private static void put(
            Map<String, Term> terms, String raw, Optional<TopicGroup> group, Source source, Set<String> stopwords) {
        String term = normaliseTerm(raw);
        if (term.length() < MIN_TERM_LENGTH || stopwords.contains(term)) {
            return;
        }
        if (term.chars().noneMatch(Character::isLetter)) {
            return;
        }
        Term existing = terms.get(term);
        if (existing == null) {
            terms.put(term, new Term(term, group, source));
        } else if (existing.group().isEmpty() && group.isPresent() && existing.source() == source) {
            // Keep the first attribution that actually claims a subject.
            terms.put(term, new Term(term, group, source));
        }
    }

    /**
     * The first in-scope term the message carries, or empty when it carries
     * none. One term is enough (research.md §5).
     */
    Optional<Term> match(String normalised) {
        List<String> tokens = tokenise(normalised);
        for (int size = Math.min(maxWords, MAX_TERM_WORDS); size >= 1; size--) {
            for (int i = 0; i + size <= tokens.size(); i++) {
                String candidate = String.join(" ", tokens.subList(i, i + size));
                Term term = byTerm.get(candidate);
                if (term != null) {
                    return Optional.of(term);
                }
            }
        }
        return Optional.empty();
    }

    /** Tokenise on anything that is not a letter or digit — this is the word boundary. */
    private static List<String> tokenise(String normalised) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < normalised.length(); i++) {
            char c = normalised.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                token.append(c);
            } else if (!token.isEmpty()) {
                tokens.add(token.toString());
                token.setLength(0);
            }
        }
        if (!token.isEmpty()) {
            tokens.add(token.toString());
        }
        return tokens;
    }

    private static String normaliseTerm(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        boolean space = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = Character.toLowerCase(raw.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                if (space && !out.isEmpty()) {
                    out.append(' ');
                }
                space = false;
                out.append(c);
            } else {
                space = true;
            }
        }
        return out.toString();
    }

    private static String readable(String constant) {
        return constant.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String ordinal(int n) {
        String suffix = switch (n % 10) {
            case 1 -> n == 11 ? "th" : "st";
            case 2 -> n == 12 ? "th" : "nd";
            case 3 -> n == 13 ? "th" : "rd";
            default -> "th";
        };
        return n + suffix;
    }

    private static Set<String> loadStopwords() {
        Tsv.Table table = Tsv.read(STOPWORDS_RESOURCE);
        int wordIndex = table.columnIndex("word");
        if (wordIndex < 0) {
            throw new IllegalStateException(STOPWORDS_RESOURCE + " needs a 'word' column");
        }
        Set<String> stopwords = new HashSet<>();
        for (List<String> record : table.records()) {
            String word = normaliseTerm(table.cell(record, wordIndex));
            if (!word.isBlank()) {
                stopwords.add(word);
            }
        }
        return Set.copyOf(stopwords);
    }

    private static void loadSupplement(Map<String, Term> terms, Set<String> stopwords) {
        Tsv.Table table = Tsv.read(SUPPLEMENT_RESOURCE);
        int termIndex = table.columnIndex("term");
        int groupIndex = table.columnIndex("group");
        if (termIndex < 0 || groupIndex < 0) {
            throw new IllegalStateException(SUPPLEMENT_RESOURCE + " needs 'term' and 'group' columns");
        }
        for (List<String> record : table.records()) {
            String term = normaliseTerm(table.cell(record, termIndex));
            String rawGroup = table.cell(record, groupIndex);
            if (term.isBlank()) {
                continue;
            }
            // Load-time validation (data-model "Validation at load"): a supplement
            // term that is also a stopword would be silently dropped, and a group
            // that does not exist is a typo that must break the build, not the gate.
            if (stopwords.contains(term)) {
                throw new IllegalStateException(
                        "supplement term '" + term + "' is also a stopword, so it would never match");
            }
            TopicGroup group;
            try {
                group = TopicGroup.valueOf(rawGroup);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "supplement term '" + term + "' names an unknown TopicGroup: " + rawGroup, e);
            }
            terms.put(term, new Term(term, Optional.of(group), Source.SUPPLEMENT));
        }
    }

    /** How many terms were derived. Diagnostic only. */
    int size() {
        return byTerm.size();
    }

    /** Every derived term. Diagnostic only. */
    java.util.Set<String> allTerms() {
        return byTerm.keySet();
    }

    /** Look one term up directly — for tests and diagnostics. */
    Optional<Term> lookup(String term) {
        return Optional.ofNullable(byTerm.get(normaliseTerm(term)));
    }
}
