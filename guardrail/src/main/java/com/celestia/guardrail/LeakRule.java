package com.celestia.guardrail;

import java.util.Objects;

/**
 * One named family of disclosure the output stage must catch (FR-017,
 * research.md §8).
 *
 * <p><b>What each family keys on is deliberately not a field here.</b> The six
 * signals are not one kind of thing: sheet ids are literal slugs, row references
 * are patterns, a citation frame is a template wrapped around a title, secrets
 * arrive as a caller-supplied list, another party's identifier is a shape test
 * against {@code recipientRef}, and reasoning markers are structural tags. A
 * single {@code signal} field would mis-describe at least three of them, so the
 * detail lives in {@link OutputStage} with research.md §8 as its reference.
 *
 * <p>The one invariant that survives modelling: <b>never a bare common word and
 * never a bare number.</b>
 *
 * @param family which disclosure this is
 * @param name what a withheld result reports to the operator
 */
record LeakRule(LeakFamily family, String name) {

    LeakRule {
        Objects.requireNonNull(family, "family");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a leak rule must be named");
        }
    }

    /**
     * The seven disclosure families.
     *
     * <p>{@link #SHEET_ID} and {@link #SHEET_TITLE} are separate so the operator
     * can tell a parroted slug — which points at tool-result plumbing — from a
     * parroted title, which points at the prompt. They differ in what you fix,
     * not in how severe they are.
     */
    enum LeakFamily {
        /** A toolkit sheet id: {@code 1500-rules}, {@code ready-reckoner}. */
        SHEET_ID,
        /** A sheet title that cannot occur in ordinary English: {@code ReadyReckoner}. */
        SHEET_TITLE,
        /** A row reference: {@code row 1349}, {@code <slug>:r<n>}. */
        ROW_REFERENCE,
        /** A prose-colliding title inside a citation frame: "per the Relationship sheet". */
        CITATION_FRAME,
        /** The system prompt or a tool name, as supplied by the caller. */
        SECRET,
        /** A phone number or user id that is not the recipient's own. */
        OTHER_PARTY_IDENTIFIER,
        /** A structural reasoning marker: a {@code <thinking>} tag, a role prefix. */
        REASONING_MARKER
    }

    /** Whether a sheet title can occur in ordinary English. */
    enum ProseClass {
        /**
         * Cannot occur in natural English — {@code ReadyReckoner},
         * {@code MarriageMatching}, {@code 1500+Rules}. Keyed <b>bare</b>:
         * requiring a citation frame would buy nothing and miss the likeliest
         * leak of all, since the model is shown the title next to the id on every
         * tool result and the title is the human-readable half.
         */
        NON_PROSE,
        /**
         * Also ordinary English or KP vocabulary — {@code Relationship},
         * {@code Death}, {@code Vimshottari}. Keyed <b>only inside a citation
         * frame</b>, so "your relationship with your partner" stays deliverable.
         */
        PROSE_COLLIDING
    }
}
