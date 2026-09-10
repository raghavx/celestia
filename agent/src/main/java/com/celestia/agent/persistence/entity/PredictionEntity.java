package com.celestia.agent.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** {@code prediction} — the daily-prediction cache, keyed on (user, for_date, kind). */
@Entity
@Table(name = "prediction")
public class PredictionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "for_date", nullable = false)
    private LocalDate forDate;

    @Column(nullable = false, length = 24)
    private String kind;

    @Column(name = "engine_version", nullable = false, length = 128)
    private String engineVersion;

    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Column(length = 64)
    private String model;

    protected PredictionEntity() {}

    public PredictionEntity(Long userId, LocalDate forDate, String kind, String engineVersion,
            String payloadJson) {
        this.userId = userId;
        this.forDate = forDate;
        this.kind = kind;
        this.engineVersion = engineVersion;
        this.payloadJson = payloadJson;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getForDate() {
        return forDate;
    }

    public String getKind() {
        return kind;
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public void setEngineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }
}
