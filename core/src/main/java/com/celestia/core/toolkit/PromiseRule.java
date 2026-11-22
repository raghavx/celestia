package com.celestia.core.toolkit;

import java.util.List;

/**
 * One house-formula rule from the toolkit's rule book (SPEC-010 FR-002).
 *
 * <p>Reads: <i>the cuspal sub lord of {@link #judgedHouse}, through its star
 * lord, connects to {@link #houses} &rArr; {@link #matter}</i>. Whether a given
 * chart satisfies it is decided by {@link PromiseEvaluator}, in code, never by
 * the chat model (Constitution IV).
 *
 * <p>{@link #condition} holds any text the import could not read as a house
 * number, kept word for word — a planet, a sign type, Maraka/Badhaka, a prose
 * qualifier. It is never parsed into structure the toolkit's author did not
 * write (FR-003), and in v1 it is for the reader to judge, which is why a rule
 * carrying one is reported as "condition to check" rather than a firm promise.
 *
 * @param id {@code <sheet-id>:<row ref>}, unique
 * @param source the id of the sheet this came from, for traceability (FR-008)
 * @param category the sheet's own grouping of the rule
 * @param matter the promised event, verbatim
 * @param matterKey lower-case, punctuation-stripped {@code matter}; hits sharing
 *     one are the same promise written in several sheets, to be reported once
 *     rather than as independent confirmations (research.md &sect;8)
 * @param judgedHouse 1..12 — whose cuspal sub lord this rule judges
 * @param houses the connecting houses, in the author's order, no duplicates; may
 *     be empty, in which case the rule is retrievable but never auto-matched
 * @param condition the extra condition, verbatim; blank when there is none
 * @param sensitivity at least the source sheet's default (research.md &sect;5)
 */
public record PromiseRule(
        String id,
        String source,
        String category,
        String matter,
        String matterKey,
        int judgedHouse,
        List<Integer> houses,
        String condition,
        Sensitivity sensitivity) {

    public PromiseRule {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("rule id must not be blank");
        }
        if (matter == null || matter.isBlank()) {
            throw new IllegalArgumentException("rule " + id + ": matter must not be blank");
        }
        if (judgedHouse < 1 || judgedHouse > 12) {
            throw new IllegalArgumentException("rule " + id + ": judgedHouse out of 1..12: " + judgedHouse);
        }
        for (int h : houses) {
            if (h < 1 || h > 12) {
                throw new IllegalArgumentException("rule " + id + ": house out of 1..12: " + h);
            }
        }
        if (houses.stream().distinct().count() != houses.size()) {
            throw new IllegalArgumentException("rule " + id + ": duplicate house in " + houses);
        }
        houses = List.copyOf(houses);
    }

    /** True when the import kept text it could not read as houses. */
    public boolean hasCondition() {
        return !condition.isBlank();
    }

    /**
     * True when this rule can never be matched against a chart by houses alone
     * — it names no houses at all (for example "Saturn as 7th sub lord").
     *
     * <p>Such rules are kept and retrievable, but the evaluator skips them
     * rather than guessing (spec.md Edge Cases, FR-003).
     */
    public boolean unmatchable() {
        return houses.isEmpty();
    }
}
