package com.celestia.agent;

/**
 * The fixed texts SPEC-012 adds (T010). Product copy, set here and never
 * generated: the model writes none of these.
 *
 * <p><b>SPEC-011 owns the others.</b> The refusal redirect, the safe replacement
 * for a withheld reply and the self-harm support line are
 * {@code com.celestia.guardrail.FixedMessages}' and must not be duplicated here —
 * two copies of a guardrail's wording is one copy that can drift.
 */
public final class AgentMessages {

    /** A sender who is not entitled to an answer (FR-001). Says nothing about why. */
    public static final String NOT_OPEN_YET =
            "Thanks for your message. Celestia isn't open to new readings just yet — "
                    + "we'll be in touch when it is.";

    /** The model was unreachable, timed out, or hit the round cap (FR-010). */
    public static final String COULD_NOT_COMPLETE =
            "I couldn't complete your reading just now. Please try asking again in a few minutes.";

    /** Another turn holds the only permit (FR-010a). Not a queue — a retry. */
    public static final String BUSY_TRY_AGAIN =
            "I'm still working on a reading. Please send your question again in a minute or two.";

    /** No birth record is seeded, so no chart can be cast (US5 scenario 2). */
    public static final String BIRTH_DETAILS_MISSING =
            "Before I can read your chart I need your birth details — the date, the time "
                    + "and the place you were born.";

    /** A message this channel cannot read. Mirrors SPEC-009's behaviour for non-text content. */
    public static final String TEXT_ONLY =
            "Thanks for your message! Right now Celestia can only read text messages — "
                    + "please reply with text for now.";

    private AgentMessages() {}
}
