package com.celestia.agent.entitlement;

/**
 * Whether this sender may be answered, and on what basis (SPEC-012 FR-002,
 * contracts/entitlement-api.md).
 *
 * <p><b>A basis, not a boolean.</b> SPEC-018 will add denials for "quota
 * exhausted" and "subscription lapsed", which an operator must be able to tell
 * apart from "not a subscriber" — and a boolean would force that distinction
 * into a second call.
 *
 * <p>The two denials produce the <b>same</b> fixed message to the sender and
 * <b>different</b> lines in the log, for the same reason SPEC-011 separated
 * {@code OFF_TOPIC} from {@code UNMATCHED}: the sender must learn nothing, and
 * the operator must be able to tell a stranger from a broken config.
 */
public enum EntitlementDecision {

    /** Allowed, because the number is on the owner allow-list. */
    OWNER_ALLOW_LIST(true),

    /** A stranger. The list is usable and this number is not on it. */
    DENIED_NOT_LISTED(false),

    /**
     * The check could not be completed — the fail-closed case (FR-003). An empty
     * allow-list, or one holding something that is not a phone number at all. An
     * empty list meaning "allow all" is how a configuration slip becomes an open
     * service, so it means the opposite.
     */
    DENIED_UNAVAILABLE(false);

    private final boolean allowed;

    EntitlementDecision(boolean allowed) {
        this.allowed = allowed;
    }

    /** True only when the reply path may continue to the gate and the model. */
    public boolean allowed() {
        return allowed;
    }
}
