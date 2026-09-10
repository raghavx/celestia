package com.celestia.agent.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** {@code inbound_event} — idempotent on {@code wa_message_id} (ADR-0015). */
@Entity
@Table(name = "inbound_event")
public class InboundEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wa_message_id", nullable = false, unique = true, length = 128)
    private String waMessageId;

    @Column(name = "raw_ref", nullable = false, length = 255)
    private String rawRef;

    @Column(nullable = false, length = 16)
    private String status = "NEW";

    @Column(nullable = false)
    private int attempts = 0;

    @Column(length = 500)
    private String error;

    @Column(name = "received_at", insertable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected InboundEventEntity() {}

    public InboundEventEntity(String waMessageId, String rawRef) {
        this.waMessageId = waMessageId;
        this.rawRef = rawRef;
    }

    public Long getId() {
        return id;
    }

    public String getWaMessageId() {
        return waMessageId;
    }

    public String getRawRef() {
        return rawRef;
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

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
