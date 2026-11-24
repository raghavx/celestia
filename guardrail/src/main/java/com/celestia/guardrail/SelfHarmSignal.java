package com.celestia.guardrail;

import java.util.List;
import java.util.regex.Pattern;

/**
 * What a user's own message shows about intent to harm themselves (FR-015,
 * research.md §7).
 *
 * <p>This is the one place where two readings of the same subject matter diverge
 * completely. SPEC-010 FR-011 says a toolkit <em>rule</em> about self-harm is
 * matched and reported like any other, with the health advisory. FR-011a says a
 * <em>user</em> expressing intent gets help first. What separates them is not
 * the presence of harm words — the Difficulty sheet is full of them — but the
 * <b>grammatical construction</b>: a first-person subject, a verb of
 * wanting/intending/planning or a modal, and a harm object.
 *
 * <p><b>Intent wins when both appear.</b> "my chart says I should die" carries a
 * chart referent and an intent construction, and it gets the support line. A
 * support line shown to someone who only wanted a reading costs little; a missed
 * one is not recoverable, so the asymmetry is resolved deliberately in favour of
 * showing it.
 *
 * <p><b>English only in the first cut.</b> A Hinglish expression of intent fires
 * nothing here, and fail-closed refuses the message too — the sharpest
 * consequence of deferring multilingual support, and a launch blocker for public
 * traffic (spec.md Assumptions).
 */
public enum SelfHarmSignal {

    /** Neither construction present. */
    NONE,

    /** A first-person volitional construction with a harm term. The support line is owed. */
    INTENT,

    /** A question about what the chart indicates. Ordinary health-class content, no support line. */
    CHART_INQUIRY;

    /** Harm objects. Alone, these mean nothing — the Difficulty sheet discusses all of them. */
    private static final Pattern HARM = compile(List.of(
            "die", "dying", "died", "death", "dead",
            "suicide", "suicidal",
            "kill myself", "killing myself", "kill my self",
            "end it", "end it all", "end my life", "ending my life", "end everything",
            "take my own life", "taking my own life",
            "hurt myself", "harm myself", "cut myself", "cutting myself",
            "hang myself", "overdose", "jump off",
            "no reason to live", "nothing to live for", "not worth living", "better off dead",
            "disappear forever"));

    /**
     * A first-person subject followed, within a few words, by a verb of volition
     * or a modal. This is the construction that makes a harm term intent rather
     * than enquiry.
     */
    private static final Pattern FIRST_PERSON_VOLITION = Pattern.compile(
            "\\b(i|i'm|im|i am|i've|ive|i have)\\b(?:\\W+\\w+){0,3}?\\W+"
                    + "\\b(want|wanna|wish|going|gonna|plan|planning|intend|intending|need|"
                    + "decided|decide|ready|about|should|must|will|would|gotta|tried|trying|try)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Self-harm phrased reflexively. These carry their own first-person subject,
     * so they mean intent even inside a question — "should i kill myself" is not
     * a longevity enquiry.
     */
    private static final Pattern REFLEXIVE = compile(List.of(
            "kill myself", "killing myself", "kill my self",
            "end my life", "ending my life", "take my own life", "taking my own life",
            "hurt myself", "harm myself", "cut myself", "cutting myself", "hang myself",
            "end it all", "end everything"));

    /**
     * An opening interrogative makes the message a question about the future
     * rather than a statement of intent. "When will I die?" is one of the
     * commonest astrology questions there is; answering it with a crisis line
     * would be wrong, and would train the user to ignore the line — the harm
     * research.md §7 warned about when it rejected keying on harm words alone.
     */
    private static final Pattern OPENING_INTERROGATIVE = Pattern.compile(
            "^\\W*\\b(when|will|would|does|do|did|is|are|was|am|what|which|whats|how|can|could|should|"
                    + "shall|why|where|who|any|tell me)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Harm as the subject of an enquiry rather than an intention — the KP
     * vocabulary of longevity questions. Enough to make a chart question
     * self-harm-adjacent, never enough to imply intent.
     */
    private static final Pattern INQUIRY_HARM = compile(List.of(
            "danger to my life", "danger to life", "threat to my life", "threat to life",
            "longevity", "lifespan", "life span", "how long will i live", "maraka", "badhaka"));

    /** Things that make a message a question about a chart rather than a statement about a life. */
    private static final Pattern CHART_REFERENT = compile(List.of(
            "chart", "horoscope", "kundli", "kundali", "birth chart",
            "house", "houses", "sub lord", "star lord", "cusp", "dasha", "bhukti",
            "longevity", "lifespan", "life span", "maraka", "badhaka", "8th", "eighth house"));

    /**
     * Classify a message that has already been through {@link Normaliser}.
     *
     * @param normalisedText the normalised view — never the raw text, or an
     *     invisible character between two letters would hide the construction
     */
    public static SelfHarmSignal of(String normalisedText) {
        if (normalisedText == null || normalisedText.isBlank()) {
            return NONE;
        }
        boolean harm = HARM.matcher(normalisedText).find();
        boolean inquiryHarm = INQUIRY_HARM.matcher(normalisedText).find();
        if (!harm && !inquiryHarm) {
            return NONE;
        }

        boolean reflexive = REFLEXIVE.matcher(normalisedText).find();
        boolean question = OPENING_INTERROGATIVE.matcher(normalisedText).find();
        boolean volitional = FIRST_PERSON_VOLITION.matcher(normalisedText).find();

        // Reflexive harm is intent even inside a question: "should i kill myself"
        // carries its own first-person subject and is not an enquiry.
        if (reflexive && (volitional || !question)) {
            return INTENT;
        }
        // Otherwise intent needs the volitional construction AND the message must
        // not be framed as a question about the future.
        if (harm && volitional && !question) {
            return INTENT;
        }
        return CHART_REFERENT.matcher(normalisedText).find() ? CHART_INQUIRY : NONE;
    }

    /** Join phrases into one word-boundary alternation. Never a bare substring match. */
    private static Pattern compile(List<String> phrases) {
        String alternation = phrases.stream().map(Pattern::quote).reduce((a, b) -> a + "|" + b).orElseThrow();
        return Pattern.compile("\\b(?:" + alternation + ")\\b", Pattern.CASE_INSENSITIVE);
    }
}
