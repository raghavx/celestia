package com.celestia.agent.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** {@code geocode_cache} — the persistent impl of SPEC-007's {@code geo.GeocodeCache}. */
@Entity
@Table(name = "geocode_cache")
public class GeocodeCacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "query_norm", nullable = false, unique = true, length = 255)
    private String queryNorm;

    @Column(name = "results_json", nullable = false, columnDefinition = "json")
    private String resultsJson;

    protected GeocodeCacheEntity() {}

    public GeocodeCacheEntity(String queryNorm, String resultsJson) {
        this.queryNorm = queryNorm;
        this.resultsJson = resultsJson;
    }

    public Long getId() {
        return id;
    }

    public String getQueryNorm() {
        return queryNorm;
    }

    public String getResultsJson() {
        return resultsJson;
    }

    public void setResultsJson(String resultsJson) {
        this.resultsJson = resultsJson;
    }
}
