package com.celestia.guardrail;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One named pattern of attack (FR-004, research.md §6).
 *
 * <p>The name is what a decision reports, so it must stay stable: renaming a
 * rule changes {@code GateDecision.matchedRule} and requires updating
 * {@code corpus-adversarial.tsv} in the same change.
 *
 * @param family which of the five attack families this belongs to
 * @param name unique, stable, and what a refusal reports to the operator
 * @param matchers compiled from {@code hostile-phrases.tsv}; a phrase matches on
 *     word boundaries, never as a bare substring (FR-011)
 */
record HostileRule(Family family, String name, List<Pattern> matchers) {

    HostileRule {
        Objects.requireNonNull(family, "family");
        matchers = List.copyOf(matchers);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a hostile rule must be named — it is what a refusal reports");
        }
        if (matchers.isEmpty()) {
            throw new IllegalArgumentException("hostile rule " + name + " has no matchers");
        }
    }

    /** The five attack families of research.md §6, covering all three limbs of FR-004. */
    enum Family {
        /** "ignore previous instructions", "you are now…", jailbreak framings. */
        INSTRUCTION_OVERRIDE,
        /** Requests for the system prompt or the tool definitions. */
        PROMPT_EXTRACTION,
        /** Requests for the model's own chain of thought. */
        REASONING_EXTRACTION,
        /** Injected role markers and turn separators that forge a system turn. */
        ROLE_IMPERSONATION,
        /**
         * Asking about another person <b>identified by phone number, user id or
         * "the user who…"</b> — never merely "another person". SPEC-010 FR-012
         * deliberately allows a user to supply a second person's <em>birth
         * data</em>, so keying on the wrong thing would either leak another
         * user's data or break marriage matching.
         */
        CROSS_USER
    }

    /** True when any matcher fires against {@code view}. */
    boolean matches(String view) {
        return matchers.stream().anyMatch(p -> p.matcher(view).find());
    }
}
