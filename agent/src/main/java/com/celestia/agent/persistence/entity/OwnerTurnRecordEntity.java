package com.celestia.agent.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * {@code owner_turn_record} — one row per turn, written for the operator and the
 * practitioner (SPEC-012 FR-020).
 *
 * <p><b>It holds two message bodies</b>, the question and the delivered reply, so
 * it is personal data under Constitution VII: removed by "forget me" (FR-020a),
 * purged past its retention age (FR-020b), and sized rather than {@code TEXT} so
 * the deferred column-encryption pass can reach both without a schema change
 * (FR-020c) — the position SPEC-008 took for its own PII columns.
 *
 * <p>The tool calls and the reference→source map are JSON: they are a record for
 * review, never a query surface.
 */
@Entity
@Table(name = "owner_turn_record")
public class OwnerTurnRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    /** PII. Sized, not TEXT — see the class note (FR-020c). */
    @Column(name = "question", nullable = false, length = 4096)
    private String question;

    /** PII, derived from a message body. Absent on a fault. */
    @Column(name = "delivered_reply", length = 4096)
    private String deliveredReply;

    @Column(name = "entitlement", nullable = false, length = 16)
    private String entitlement;

    @Column(name = "gate_outcome", length = 32)
    private String gateOutcome;

    @Column(name = "gate_reason", length = 32)
    private String gateReason;

    @Column(name = "outcome", nullable = false, length = 32)
    private String outcome;

    @Column(name = "sensitivity_used", nullable = false, length = 16)
    private String sensitivityUsed;

    @Column(name = "delivered_length", nullable = false)
    private int deliveredLength;

    @Column(name = "fault_kind", length = 32)
    private String faultKind;

    /** Which model answered — the provenance a practitioner's verdict depends on (ADR-0009). */
    @Column(name = "model_used", length = 64)
    private String modelUsed;

    @Column(name = "fallback_switched", nullable = false)
    private boolean fallbackSwitched;

    /** Which disclosure family the leak scan caught, when it withheld (SC-004). */
    @Column(name = "withheld_family", length = 32)
    private String withheldFamily;

    /** Whether the reply had to be trimmed to the channel budget — a prompt defect (FR-017). */
    @Column(name = "reply_trimmed", nullable = false)
    private boolean replyTrimmed;

    // columnDefinition = "json", as every other JSON column in this schema does
    // (chart.computed_json, geocode_cache.results_json). NOT @Lob: a @Lob String
    // with no length maps to TINYTEXT — 255 bytes — in Hibernate's MySQL dialect,
    // which fails schema validation against a wider column and would silently be
    // far too small for a turn's tool calls if it ever matched.
    @Column(name = "tool_calls_json", nullable = false, columnDefinition = "json")
    private String toolCallsJson;

    @Column(name = "resolved_sources_json", nullable = false, columnDefinition = "json")
    private String resolvedSourcesJson;

    protected OwnerTurnRecordEntity() {}

    @SuppressWarnings("java:S107") // one row, one constructor; a builder here would only hide the width
    public OwnerTurnRecordEntity(
            Long userId,
            Instant receivedAt,
            String question,
            String deliveredReply,
            String entitlement,
            String gateOutcome,
            String gateReason,
            String outcome,
            String sensitivityUsed,
            int deliveredLength,
            String faultKind,
            String modelUsed,
            boolean fallbackSwitched,
            String withheldFamily,
            boolean replyTrimmed,
            String toolCallsJson,
            String resolvedSourcesJson) {
        this.userId = userId;
        this.receivedAt = receivedAt;
        this.question = question;
        this.deliveredReply = deliveredReply;
        this.entitlement = entitlement;
        this.gateOutcome = gateOutcome;
        this.gateReason = gateReason;
        this.outcome = outcome;
        this.sensitivityUsed = sensitivityUsed;
        this.deliveredLength = deliveredLength;
        this.faultKind = faultKind;
        this.modelUsed = modelUsed;
        this.fallbackSwitched = fallbackSwitched;
        this.withheldFamily = withheldFamily;
        this.replyTrimmed = replyTrimmed;
        this.toolCallsJson = toolCallsJson;
        this.resolvedSourcesJson = resolvedSourcesJson;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getQuestion() {
        return question;
    }

    public String getDeliveredReply() {
        return deliveredReply;
    }

    public String getEntitlement() {
        return entitlement;
    }

    public String getGateOutcome() {
        return gateOutcome;
    }

    public String getGateReason() {
        return gateReason;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getSensitivityUsed() {
        return sensitivityUsed;
    }

    public int getDeliveredLength() {
        return deliveredLength;
    }

    public String getFaultKind() {
        return faultKind;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public boolean isFallbackSwitched() {
        return fallbackSwitched;
    }

    public String getWithheldFamily() {
        return withheldFamily;
    }

    public boolean isReplyTrimmed() {
        return replyTrimmed;
    }

    public String getToolCallsJson() {
        return toolCallsJson;
    }

    public String getResolvedSourcesJson() {
        return resolvedSourcesJson;
    }
}
