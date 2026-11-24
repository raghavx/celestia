package com.celestia.guardrail;

import com.celestia.core.toolkit.TopicGroup;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome for one inbound message (FR-001), with the reason an operator
 * needs and nothing an attacker could use.
 *
 * <p><b>Never carries the message text.</b> {@code matchedRule} is a rule name
 * or a topic group, so a refusal log cannot quietly become a store of rejected
 * message bodies (Constitution VII, research.md §9).
 *
 * @param outcome one of FR-001's three outcomes
 * @param reason why, for the operator; the user never sees this
 * @param matchedRule the name of the rule or group that decided it — never the
 *     message text
 * @param topicGroup <b>best-effort diagnostic only.</b> Present only when
 *     allowed, naming the most specific group the matched vocabulary could be
 *     attributed to. Often absent or generic even for a clearly on-topic
 *     message, because the master rule book's vocabulary spans every subject and
 *     so carries no subject claim. No decision depends on it and nothing asserts
 *     it (research.md §12 finding 1, §13)
 */
public record GateDecision(Outcome outcome, Reason reason, String matchedRule, Optional<TopicGroup> topicGroup) {

    public GateDecision {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(topicGroup, "topicGroup");
        if (matchedRule == null || matchedRule.isBlank()) {
            throw new IllegalArgumentException("every decision must name what decided it (FR-010, SC-007)");
        }
    }

    /** FR-001's three outcomes. The user-facing text is identical for the two refusals (FR-008). */
    public enum Outcome {
        ALLOWED,
        REFUSED_OUT_OF_SCOPE,
        REFUSED_HOSTILE
    }

    /**
     * Why a message was decided as it was: the four refusal classes FR-010
     * requires be countable apart, plus {@code IN_SCOPE} for an allowed one.
     *
     * <p>{@link #OFF_TOPIC} and {@link #UNMATCHED} produce identical user-facing
     * text. They are separate values so the second can be counted on its own —
     * it is the measure of how often failing closed refuses a genuine question
     * (SC-007).
     */
    public enum Reason {
        /** Vocabulary for an answerable topic group matched. */
        IN_SCOPE,
        /** A hostile rule family matched. */
        HOSTILE,
        /** Past the bounded length (FR-012). */
        OVERSIZE,
        /** Recognised as off-topic — working as intended. */
        OFF_TOPIC,
        /** Could not be placed at all. <b>The false-refusal signal.</b> */
        UNMATCHED
    }

    static GateDecision allowed(String matchedRule, Optional<TopicGroup> group) {
        return new GateDecision(Outcome.ALLOWED, Reason.IN_SCOPE, matchedRule, group);
    }

    static GateDecision hostile(Reason reason, String matchedRule) {
        return new GateDecision(Outcome.REFUSED_HOSTILE, reason, matchedRule, Optional.empty());
    }

    static GateDecision outOfScope(Reason reason, String matchedRule) {
        return new GateDecision(Outcome.REFUSED_OUT_OF_SCOPE, reason, matchedRule, Optional.empty());
    }

    /** True when the answering model may be invoked for this message. */
    public boolean isAllowed() {
        return outcome == Outcome.ALLOWED;
    }
}
