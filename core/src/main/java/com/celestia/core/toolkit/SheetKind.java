package com.celestia.core.toolkit;

/**
 * The shape of a toolkit sheet's content, which decides how it is used
 * (SPEC-010 data-model.md "Sheet").
 */
public enum SheetKind {
    /** House-formula promise rules, parsed into {@link PromiseRule}s (FR-002). */
    RULES,
    /** A keyed look-up: nakshatra-quarter, planet &times; house, planet, sign, house. */
    TABLE,
    /** Ordered steps an astrologer follows; retrieved in row order (FR-007). */
    PROCEDURE,
    /** A worksheet to fill in — read in row order, like a procedure. */
    TEMPLATE,
    /** Gems, yantras and similar; carries the remedy advisory (FR-010a). */
    REMEDY,
    /** Index, version log, software list. Never answer content (FR-014). */
    HOUSEKEEPING
}
