package com.celestia.agent.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * {@code dasha_period} — a flattened dasha projection. SPEC-008 creates the table
 * for a stable FK but does <b>not</b> write it; SPEC-013 owns population.
 */
@Entity
@Table(name = "dasha_period")
public class DashaPeriodEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chart_id", nullable = false)
    private Long chartId;

    @Column(nullable = false, length = 16)
    private String level;

    @Column(nullable = false, length = 12)
    private String lord;

    @Column(name = "start_utc", nullable = false)
    private Instant startUtc;

    @Column(name = "end_utc", nullable = false)
    private Instant endUtc;

    @Column(name = "parent_id")
    private Long parentId;

    protected DashaPeriodEntity() {}

    public Long getId() {
        return id;
    }

    public Long getChartId() {
        return chartId;
    }

    public String getLevel() {
        return level;
    }

    public String getLord() {
        return lord;
    }

    public Instant getStartUtc() {
        return startUtc;
    }

    public Instant getEndUtc() {
        return endUtc;
    }

    public Long getParentId() {
        return parentId;
    }
}
