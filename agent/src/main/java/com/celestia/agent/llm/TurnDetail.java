package com.celestia.agent.llm;

import com.celestia.core.toolkit.Sensitivity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What one turn produced, as the reply handler hands it to the store (FR-020,
 * data-model.md "TurnRecord").
 *
 * <p><b>A turn is a message that acquired the turn permit</b> (FR-010c). A message
 * stopped before that point — no entitlement, not text, no permit — produces no
 * record at all, because no turn happened; those are counted instead
 * ({@code celestia.agent.turned_away}).
 *
 * <p>Everything from the permit on is recorded whatever happened, faults
 * included: an operator must be able to tell a fault from a refusal, and a
 * practitioner must be able to review the answer without reading code.
 *
 * <p><b>It holds two message bodies</b> — the question and the delivered reply —
 * so it is personal data under Constitution VII. Both are covered by the
 * "forget me" hard delete (FR-020a) and the retention purge (FR-020b), and both
 * are stored in a shape the deferred column-encryption pass can reach without a
 * schema change (FR-020c).
 */
public record TurnDetail(
        long userId,
        Instant receivedAt,
        String question,
        Entitlement entitlement,
        Optional<String> gateOutcome,
        Optional<String> gateReason,
        Outcome outcome,
        List<ToolCallRecord> toolCalls,
        Map<String, SourceRef> resolvedSources,
        Sensitivity sensitivityUsed,
        Optional<String> deliveredReply,
        int deliveredLength,
        Optional<FaultKind> faultKind,
        Optional<String> modelUsed,
        boolean fallbackSwitched,
        Optional<String> withheldFamily,
        boolean replyTrimmed) {

    public TurnDetail {
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(entitlement, "entitlement");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(sensitivityUsed, "sensitivityUsed");
        toolCalls = List.copyOf(toolCalls);
        resolvedSources = Map.copyOf(resolvedSources);
    }

    /** The first decision made about this sender (FR-001). */
    public enum Entitlement {
        ALLOWED,
        DENIED
    }

    /**
     * How the turn ended.
     *
     * <p>The three agent outcomes are what a single {@code REFUSED} used to hide,
     * and they have different owners: a decline the toolkit genuinely could not
     * answer is the product working, a redirect for a question it <i>does</i>
     * cover is a prompt defect, and a gate refusal is a vocabulary gap in
     * SPEC-011. SC-007 counts the directions apart, and that is only possible if
     * the record keeps them apart.
     */
    public enum Outcome {
        /** A substantive answer reached the owner. */
        ANSWERED,
        /** The agent said it had no basis, in its own voice (FR-014). */
        AGENT_NO_BASIS,
        /** The model emitted the out-of-scope marker and the output stage substituted the redirect. */
        AGENT_REDIRECT,
        /** The gate never let the question through (SPEC-011). */
        GATE_REFUSED,
        /** The leak scan withheld the draft — correct behaviour, and a prompt defect (SC-004). */
        WITHHELD,
        /** The model was unreachable, timed out, or hit the round cap (FR-010). */
        FAULT
    }

    /**
     * Why a turn faulted.
     *
     * <p><b>Malformed tool JSON is not here.</b> It switches the turn to the
     * fallback model (ADR-0009) and is recorded as {@link #fallbackSwitched()} —
     * on a small model that is the normal path, not a fault. It only becomes
     * {@link #MALFORMED_TOOL_JSON} when the fallback failed the same way, which is
     * the point at which nobody is going to answer the question.
     */
    public enum FaultKind {
        /** The turn outlived {@code celestia.llm.turn-deadline} — a hosting problem. */
        TIMEOUT,
        /** The model kept calling tools past the cap — a prompt problem. */
        ROUND_CAP,
        /** Neither the primary model nor the fallback could be reached. */
        MODEL_UNREACHABLE,
        /** Both models produced tool calls that could not be read. */
        MALFORMED_TOOL_JSON
    }

    /** A turn that produced a delivered reply. */
    public static Builder builder(long userId, Instant receivedAt, String question) {
        return new Builder(userId, receivedAt, question);
    }

    /**
     * Assembles a detail across the order of operations — the handler learns the
     * entitlement, then the gate decision, then what the model did, in that order
     * (data-model.md "Order of operations").
     */
    public static final class Builder {
        private final long userId;
        private final Instant receivedAt;
        private final String question;
        private Entitlement entitlement = Entitlement.ALLOWED;
        private String gateOutcome;
        private String gateReason;
        private Outcome outcome = Outcome.FAULT;
        private List<ToolCallRecord> toolCalls = List.of();
        private Map<String, SourceRef> resolvedSources = Map.of();
        private Sensitivity sensitivityUsed = Sensitivity.NONE;
        private String deliveredReply;
        private int deliveredLength;
        private FaultKind faultKind;
        private String modelUsed;
        private boolean fallbackSwitched;
        private String withheldFamily;
        private boolean replyTrimmed;

        private Builder(long userId, Instant receivedAt, String question) {
            this.userId = userId;
            this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
            this.question = Objects.requireNonNull(question, "question");
        }

        public Builder entitlement(Entitlement value) {
            this.entitlement = value;
            return this;
        }

        public Builder gate(String outcomeName, String reasonName) {
            this.gateOutcome = outcomeName;
            this.gateReason = reasonName;
            return this;
        }

        public Builder outcome(Outcome value) {
            this.outcome = value;
            return this;
        }

        public Builder toolCalls(List<ToolCallRecord> value) {
            this.toolCalls = value;
            return this;
        }

        public Builder resolvedSources(Map<String, SourceRef> value) {
            this.resolvedSources = value;
            return this;
        }

        public Builder sensitivityUsed(Sensitivity value) {
            this.sensitivityUsed = value;
            return this;
        }

        public Builder deliveredReply(String value) {
            this.deliveredReply = value;
            this.deliveredLength = value == null ? 0 : value.length();
            return this;
        }

        public Builder faultKind(FaultKind value) {
            this.faultKind = value;
            return this;
        }

        public Builder model(String name, boolean switchedToFallback) {
            this.modelUsed = name;
            this.fallbackSwitched = switchedToFallback;
            return this;
        }

        public Builder withheldFamily(String value) {
            this.withheldFamily = value;
            return this;
        }

        public Builder replyTrimmed(boolean value) {
            this.replyTrimmed = value;
            return this;
        }

        public TurnDetail build() {
            return new TurnDetail(
                    userId,
                    receivedAt,
                    question,
                    entitlement,
                    Optional.ofNullable(gateOutcome),
                    Optional.ofNullable(gateReason),
                    outcome,
                    toolCalls,
                    resolvedSources,
                    sensitivityUsed,
                    Optional.ofNullable(deliveredReply),
                    deliveredLength,
                    Optional.ofNullable(faultKind),
                    Optional.ofNullable(modelUsed),
                    fallbackSwitched,
                    Optional.ofNullable(withheldFamily),
                    replyTrimmed);
        }
    }
}
