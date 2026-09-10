package com.celestia.agent.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** {@code horary_query} — the question metadata for a horary {@code chart}. */
@Entity
@Table(name = "horary_query")
public class HoraryQueryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chart_id", nullable = false, unique = true)
    private Long chartId;

    @Column(name = "number_1_249", nullable = false)
    private short number;

    @Column(length = 1000)
    private String question;

    @Column(name = "judged_utc", nullable = false)
    private Instant judgedUtc;

    protected HoraryQueryEntity() {}

    public HoraryQueryEntity(Long chartId, short number, String question, Instant judgedUtc) {
        this.chartId = chartId;
        this.number = number;
        this.question = question;
        this.judgedUtc = judgedUtc;
    }

    public Long getId() {
        return id;
    }

    public Long getChartId() {
        return chartId;
    }

    public short getNumber() {
        return number;
    }

    public String getQuestion() {
        return question;
    }

    public Instant getJudgedUtc() {
        return judgedUtc;
    }
}
