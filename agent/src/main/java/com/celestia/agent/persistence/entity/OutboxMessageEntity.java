package com.celestia.agent.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
}
