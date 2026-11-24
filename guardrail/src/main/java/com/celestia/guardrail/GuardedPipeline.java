package com.celestia.guardrail;

import com.celestia.core.toolkit.Sensitivity;
import java.util.Objects;

/**
 * The only type that calls an {@link Answerer}, and the reason Constitution V's
 * single route is structural rather than documentary (FR-003, FR-013).
 *
 * <p>An ArchUnit rule in {@code agent} forbids any class outside this one from
 * depending on a chat-model type, so there is no second path to write.
 *
 * <h2>What happens to a message</h2>
 *
 * <ol>
 *   <li>Normalise once, and derive the {@link SelfHarmSignal} — for <b>every</b>
 *       message, allowed or refused, because the support line is owed on what the
 *       sender wrote and not on what the gate decided (FR-015).
 *   <li>{@link Gate#decide}.
 *   <li>If <b>not</b> allowed: the answerer is <b>not called</b> — the property
 *       SC-001 measures — and the refusal still goes through the output stage, so
 *       no reply of any kind leaves this module without passing it (FR-013). A
 *       refused message carrying self-harm intent therefore still receives the
 *       support line.
 *   <li>If allowed: call the answerer exactly once, then decorate its draft.
 * </ol>
 */
public final class GuardedPipeline {

    private final Gate gate;
    private final OutputStage output;

    public GuardedPipeline(Gate gate, OutputStage output) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.output = Objects.requireNonNull(output, "output");
    }

    /** The shared pipeline over the bundled toolkit. */
    public static GuardedPipeline bundled() {
        return new GuardedPipeline(Gate.bundled(), OutputStage.bundled());
    }

    /**
     * Gate the message, answer it only if allowed, and decorate whatever comes
     * back.
     *
     * @return what to send the user — never null, never the raw draft
     */
    public OutputResult handle(GateRequest request, Answerer.AnswerContext context, Answerer answerer) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(answerer, "answerer");

        // The signal is taken from the normalised view truncated to the bound, so
        // padding a message past 4,096 characters cannot drop the support line.
        String forSignal = request.text().length() > Gate.MAX_MESSAGE_LENGTH
                ? request.text().substring(0, Gate.MAX_MESSAGE_LENGTH)
                : request.text();
        SelfHarmSignal selfHarm = SelfHarmSignal.of(Normaliser.normalise(forSignal).normalised());

        GateDecision decision = gate.decide(request);
        if (!decision.isAllowed()) {
            // The answerer is never constructed, called or consulted here.
            return output.decorate(OutputRequest.refusal(selfHarm, context.recipientRef()));
        }

        Answerer.Answer answer = answerer.answer(request, context);
        Sensitivity sensitivityUsed = answer.sensitivityUsed();
        return output.decorate(new OutputRequest(
                answer.draft(), sensitivityUsed, selfHarm, context.recipientRef(), context.secrets()));
    }

    /** The decision alone, without answering — for callers that only need to gate. */
    public GateDecision gate(GateRequest request) {
        return gate.decide(request);
    }
}
