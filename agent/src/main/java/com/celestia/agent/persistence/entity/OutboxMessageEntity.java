package com.celestia.agent.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** {@code outbox_message} — outbound queue with attempt/error tracking (ADR-0015). */
@Entity
@Table(name = "outbox_message")
public class OutboxMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 64)
    private String target;

    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(nullable = false)
    private int attempts = 0;

    @Column(length = 500)
    private String error;

    /** SPEC-009: the provider's assigned message id, set on a successful send. */
    @Column(name = "provider_message_id", length = 64)
    private String providerMessageId;

    /** SPEC-009: the retry-backoff gate; {@code null} claims immediately. */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    /** SPEC-009 US6: the reclaim-sweep staleness clock. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected OutboxMessageEntity() {}

    public OutboxMessageEntity(Long userId, String target, String payloadJson) {
        this.userId = userId;
        this.target = target;
        this.payloadJson = payloadJson;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTarget() {
        return target;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
