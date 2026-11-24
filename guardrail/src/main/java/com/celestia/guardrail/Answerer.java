package com.celestia.guardrail;

import com.celestia.core.toolkit.Sensitivity;
import java.util.List;
import java.util.Objects;

/**
 * The seam SPEC-012 implements over the LLM — and the reason "no model was
 * called" is countable before any model exists (research.md §11).
 *
 * <p>{@link GuardedPipeline} is the only type that calls this, and it calls it
 * only on an allowed decision. The adversarial corpus wires a counting fake and
 * asserts zero invocations across the whole file, so SC-001 is a measurement
 * today rather than a promise for when the model lands.
 *
 * <p>Nothing else may implement a path to a chat model: an ArchUnit rule in
 * {@code agent} forbids any class outside {@code GuardedPipeline} from depending
 * on a chat-model type (Constitution V).
 */
@FunctionalInterface
public interface Answerer {

    /**
     * Produce a draft reply for a message the gate allowed.
     *
     * @param request the message, unchanged — the gate does not rewrite it
     * @param context what only the caller knows: who the reply is for, and the
     *     secrets the output stage must scan for
     */
    Answer answer(GateRequest request, AnswerContext context);

    /**
     * What the answerer produced.
     *
     * @param draft the reply text, before the output stage decorates it
     * @param sensitivityUsed the most severe sensitivity class the answer drew
     *     on, <b>reported by the caller</b> from its toolkit tool results — never
     *     inferred from the draft text, because inferring it would make the
     *     advisory sentence depend on wording rather than on content (FR-014)
     */
    record Answer(String draft, Sensitivity sensitivityUsed) {
        public Answer {
            Objects.requireNonNull(draft, "draft");
            Objects.requireNonNull(sensitivityUsed, "sensitivityUsed");
        }
    }

    /**
     * What the output stage needs and only the caller can know.
     *
     * @param recipientRef an opaque reference to whoever the reply is going to,
     *     so an identifier in the draft that is <em>not</em> theirs is detectable
     *     (FR-017)
     * @param secrets the system prompt text and the tool names — known to
     *     SPEC-012, not to this module, so they are passed in rather than guessed
     */
    record AnswerContext(String recipientRef, List<String> secrets) {
        public AnswerContext {
            Objects.requireNonNull(recipientRef, "recipientRef");
            secrets = List.copyOf(secrets);
        }

        /** A context with no secrets to scan for — for tests and for callers that have none. */
        public static AnswerContext of(String recipientRef) {
            return new AnswerContext(recipientRef, List.of());
        }
    }
}
