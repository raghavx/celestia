package com.celestia.core.toolkit;

import java.util.List;

/**
 * One toolkit rule judged against one chart (SPEC-010 FR-005).
 *
 * @param rule the rule judged; its {@code judgedHouse} is the house this hit sits under
 * @param connected the rule's houses the chart does connect, in the rule's order
 * @param missing the rule's houses it does not
 * @param status how firmly the chart satisfies the rule
 */
public record PromiseHit(PromiseRule rule, List<Integer> connected, List<Integer> missing, Status status) {

    /**
     * How firmly a chart satisfies a rule.
     *
     * <p>The distinction matters in the reply: a {@link #CONDITION_TO_CHECK} hit
     * must never be stated as a firm promise, and a {@link #PARTLY_INDICATED} one
     * must be said as "partly indicated" or not at all
     * (contracts/agent-tools.md "Answer obligations").
     */
    public enum Status {
        /** Every house connects and the rule carries no extra condition. */
        PROMISED,
        /**
         * Every house connects, but the rule carries a condition the system does
         * not decide (a planet, a sign type, Maraka/Badhaka). For the reader to
         * confirm — never reported as a firm promise.
         */
        CONDITION_TO_CHECK,
        /**
         * Some houses connect and some do not. Useful context, returned only when
         * asked for, and never presented as a promise (research.md &sect;4).
         */
        PARTLY_INDICATED
    }

    public PromiseHit {
        connected = List.copyOf(connected);
        missing = List.copyOf(missing);
        if (status == Status.PROMISED && (!missing.isEmpty() || rule.hasCondition())) {
            throw new IllegalArgumentException(
                    "rule " + rule.id() + ": PROMISED requires every house connected and no condition");
        }
        if (status == Status.CONDITION_TO_CHECK && (!missing.isEmpty() || !rule.hasCondition())) {
            throw new IllegalArgumentException(
                    "rule " + rule.id() + ": CONDITION_TO_CHECK requires every house connected and a condition");
        }
        if (status == Status.PARTLY_INDICATED && (connected.isEmpty() || missing.isEmpty())) {
            throw new IllegalArgumentException(
                    "rule " + rule.id() + ": PARTLY_INDICATED requires both connected and missing houses");
        }
    }

    /** The house whose cuspal sub lord this hit was judged from. */
    public int house() {
        return rule.judgedHouse();
    }

    /** Groups hits that are the same promise written in several sheets. */
    public String matterKey() {
        return rule.matterKey();
    }

    /** The rule's own sensitivity class. */
    public Sensitivity sensitivity() {
        return rule.sensitivity();
    }
}
