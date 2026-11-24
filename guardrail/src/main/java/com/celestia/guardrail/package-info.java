/**
 * The deterministic guardrail: the only route from a user message to an answer.
 *
 * <p>Two halves of one path (Constitution V, NON-NEGOTIABLE). Inbound, {@link
 * com.celestia.guardrail.Gate} decides every message before the answering model
 * is invoked: a hostile rule, then evidence that the message belongs to a
 * toolkit topic group with answerable content. Anything it cannot place is
 * refused — fail closed — and the reason is recorded so false refusals stay
 * countable. Outbound, {@link com.celestia.guardrail.OutputStage} appends the
 * one advisory sentence the answer earned, prepends the crisis support line when
 * the user's own message showed intent, and withholds any reply that would
 * disclose the system prompt, another user's data, or the toolkit's identity.
 *
 * <p>{@link com.celestia.guardrail.GuardedPipeline} is the only type that calls
 * an {@link com.celestia.guardrail.Answerer}, and it calls it only on an allowed
 * decision. An ArchUnit rule in {@code agent} forbids any other path to a chat
 * model, so "exactly one route" is a property of the code rather than an
 * intention.
 *
 * <p><b>Pure and deterministic</b> (Constitution II): no Spring, no database, no
 * network, no clock, no ambient state. The same message always produces the same
 * decision. Resources are classpath TSVs, read once.
 *
 * <h2>Deliberately not here</h2>
 *
 * <ul>
 *   <li>The small-model classifier and the embedding fallback — ADR-0023 options
 *       1 and 3, deferred to SPEC-021. This module is ADR-0023's layer 1.
 *   <li>The LLM and the reply wiring — SPEC-012 supplies the {@code Answerer}.
 *   <li>Repeated-abuse handling (strike counts, cool-off) — SPEC-021.
 *   <li>Hindi and Hinglish recognition — post-launch. The matcher stays
 *       script-aware and table-driven so adding a language is data, not surgery.
 * </ul>
 *
 * <h2>Extending it</h2>
 *
 * <p>Adding a {@code TopicGroup} to the toolkit widens the gate with no API
 * change. Adding a hostile phrase or an in-scope term is a resource change.
 * Renaming a hostile rule changes what decisions report, so it requires updating
 * {@code corpus-adversarial.tsv} in the same change.
 *
 * @see <a href="file:../../../../../../specs/011-guardrail-core/spec.md">SPEC-011</a>
 */
package com.celestia.guardrail;
