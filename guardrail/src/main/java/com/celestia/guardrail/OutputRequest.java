package com.celestia.guardrail;

import com.celestia.core.toolkit.Sensitivity;
import java.util.List;
import java.util.Objects;

/**
 * What the output stage is given. The caller supplies everything the stage must
 * not have to guess.
 *
 * @param draft what the answerer produced — or, on a refusal, the fixed redirect
 * @param sensitivityUsed the most severe class the answer drew on, <b>reported
 *     by the caller</b> from its toolkit tool results and never inferred from the
 *     draft text; {@code NONE} on a refusal (FR-014)
 * @param selfHarm computed by {@link GuardedPipeline} from the inbound message,
 *     for refused decisions as well as allowed ones (FR-015)
 * @param recipientRef who the reply is going to, so an identifier in the draft
 *     that is <em>not</em> theirs is detectable (FR-017)
 * @param secrets the system prompt and tool names, known only to SPEC-012
 */
public record OutputRequest(
        String draft,
        Sensitivity sensitivityUsed,
        SelfHarmSignal selfHarm,
        String recipientRef,
        List<String> secrets) {

    public OutputRequest {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(sensitivityUsed, "sensitivityUsed");
        Objects.requireNonNull(selfHarm, "selfHarm");
        Objects.requireNonNull(recipientRef, "recipientRef");
        secrets = List.copyOf(secrets);
    }

    /**
     * The refused case: the fixed redirect as the draft, nothing sensitive, no
     * secrets to scan for.
     *
     * <p>Routing a refusal through the output stage is what makes the support
     * line reach someone whose message the gate could not place. The fixed
     * decoration order then does the right thing by itself — product copy
     * carries no marker, no secret and no sensitivity, so the only decoration a
     * refusal can receive is the support line (FR-013, FR-015).
     */
    public static OutputRequest refusal(SelfHarmSignal selfHarm, String recipientRef) {
        return new OutputRequest(
                FixedMessages.refusalRedirect(), Sensitivity.NONE, selfHarm, recipientRef, List.of());
    }
}
