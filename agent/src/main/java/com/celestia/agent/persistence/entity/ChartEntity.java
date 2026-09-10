package com.celestia.agent.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code chart} — the engine-versioned cache row. {@code computed_json} holds the raw engine outputs. */
@Entity
@Table(name = "chart")
public class ChartEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(nullable = false, length = 32)
    private String ayanamsa;

    @Column(name = "house_system", nullable = false, length = 16)
    private String houseSystem;

    @Column(name = "engine_version", nullable = false, length = 128)
    private String engineVersion;

    @Column(name = "computed_json", nullable = false, columnDefinition = "json")
    private String computedJson;

    protected ChartEntity() {}

    public ChartEntity(Long userId, String kind, String ayanamsa, String houseSystem,
            String engineVersion, String computedJson) {
        this.userId = userId;
        this.kind = kind;
        this.ayanamsa = ayanamsa;
        this.houseSystem = houseSystem;
        this.engineVersion = engineVersion;
        this.computedJson = computedJson;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getKind() {
        return kind;
    }

    public String getAyanamsa() {
        return ayanamsa;
    }

    public String getHouseSystem() {
        return houseSystem;
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public void setEngineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
    }

    public String getComputedJson() {
        return computedJson;
    }

    public void setComputedJson(String computedJson) {
        this.computedJson = computedJson;
    }
}
