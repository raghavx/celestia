package com.celestia.core.toolkit;

/**
 * The subject a toolkit sheet belongs to (SPEC-010 data-model.md "Sheet").
 *
 * <p>This is also the taxonomy the input guardrail's topic gate is built from
 * (Constitution V: in scope is every toolkit topic except {@link #HOUSEKEEPING}),
 * so adding a constant widens what the product will answer. Adding one is not a
 * breaking API change; removing or renaming one is.
 */
public enum TopicGroup {
    /** Lords, houses, aspects, planet characteristics — the KP vocabulary itself. */
    KP_FOUNDATIONS,
    /** The 1–249 number system and its mindsets. */
    HORARY,
    /** How an astrologer proceeds: ruling planets, ABCD, timing, BTR, annual. */
    PREDICTION_METHOD,
    /** General life matters: luck, difficulty, litigation. */
    LIFE_MATTERS,
    MARRIAGE_RELATIONSHIPS,
    CAREER_EDUCATION,
    /** Finance and speculation — carries the finance advisory (FR-010a). */
    FINANCE_SPECULATION,
    PROPERTY,
    /** Disease and treatment — carries the health advisory (FR-010a). */
    HEALTH_MEDICAL,
    /** Lifespan and manner of death — carries the longevity advisory (FR-010a). */
    LONGEVITY,
    TRAVEL,
    CHILDBIRTH,
    /** Gems, yantras — carries the remedy advisory (FR-010a). */
    REMEDIES,
    VASTU_FENG_SHUI,
    NUMEROLOGY,
    /** Look-up material that is not a KP rule (zodiac places, and similar). */
    REFERENCE,
    /** The index, the version log, the software list. Never answer content (FR-014). */
    HOUSEKEEPING
}
