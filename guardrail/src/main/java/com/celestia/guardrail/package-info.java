/**
 * The astrology-only conversation guardrail.
 *
 * <p>Deterministic injection/jailbreak detection, a topic gate backed by an
 * intent classifier, and an output filter. Returns a {@code GateDecision}; the
 * chat model is never reachable except through this module. See SPEC-014.
 */
package com.celestia.guardrail;
