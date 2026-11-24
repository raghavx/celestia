package com.celestia.guardrail;

/**
 * The product's own copy. <b>This is the only source of these three strings</b>
 * (FR-008, FR-015, FR-018): they are set here, never generated, never reworded
 * per reply, and never written by the chat model.
 *
 * <p>The four <b>advisory</b> sentences are deliberately <em>not</em> here. They
 * belong to SPEC-010's {@code Sensitivity.note()}, which is their single source
 * (Constitution V). Copying them into this class would create the second source
 * that principle exists to forbid.
 */
public final class FixedMessages {

    /**
     * The single refusal text, identical whether the refusal was for being out
     * of scope or for being hostile (FR-008), so a probe learns nothing about
     * what was detected. The only permitted addition is the support line below.
     */
    private static final String REFUSAL_REDIRECT =
            "I can only help with astrology questions — your chart, timing, marriage, career, "
                    + "health, remedies, Vastu or numerology. Ask me one of those and I'll take a look.";

    /** What replaces a reply the output stage withheld in full (FR-018). */
    private static final String SAFE_REPLACEMENT =
            "Sorry — I couldn't put that answer together properly. Please ask me again, "
                    + "and I'll work through your chart afresh.";

    /**
     * Prepended when the user's own message expresses intent to harm themselves
     * (FR-015), before any astrological content. Tele-MANAS 14416 is India's
     * national mental-health helpline, as confirmed for SPEC-010 FR-011a.
     */
    private static final String SELF_HARM_SUPPORT_LINE =
            "Before anything else: please talk to someone tonight. Tele-MANAS is free, "
                    + "confidential and open 24x7 — call 14416. You deserve support from a person, "
                    + "not a chart.";

    private FixedMessages() {}

    /** The single refusal text (FR-008). */
    public static String refusalRedirect() {
        return REFUSAL_REDIRECT;
    }

    /** What replaces a withheld reply (FR-018). */
    public static String safeReplacement() {
        return SAFE_REPLACEMENT;
    }

    /** The crisis support line, naming Tele-MANAS 14416 (FR-015). */
    public static String selfHarmSupportLine() {
        return SELF_HARM_SUPPORT_LINE;
    }
}
