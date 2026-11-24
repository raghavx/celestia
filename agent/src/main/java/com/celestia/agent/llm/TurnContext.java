package com.celestia.agent.llm;

import com.celestia.agent.persistence.store.NatalChartBundle;
import com.celestia.core.chart.NatalChart;
import java.util.Objects;
import java.util.Optional;

/**
 * What is assembled <b>before</b> the model is invoked, so the model never has to
 * ask for the basics (FR-009, data-model.md "TurnContext").
 *
 * <p>The chart and the running dasha are pre-fetched by the reply handler. That
 * removes a whole class of failure — the model asking for, guessing at or
 * mis-ordering the basics — and it is what keeps the first answer fast enough to
 * feel like a conversation.
 *
 * @param userId the authenticated sender, from the verified phone number — never
 *     read from message text (FR-006)
 * @param senderE164 the verified number, for the output stage's leak scan
 * @param question the inbound message, unchanged; the gate has already allowed it
 * @param chart the pre-fetched natal chart, significators and dasha timeline
 * @param suppliedChart a chart cast from a second person's birth data given in
 *     <b>this</b> message. <b>Dies with the turn</b> — it is never persisted, and
 *     nothing in this record is written anywhere but the turn's own log (FR-011)
 */
public record TurnContext(
        long userId,
        String senderE164,
        String question,
        NatalChartBundle chart,
        Optional<NatalChart> suppliedChart) {

    public TurnContext {
        Objects.requireNonNull(senderE164, "senderE164");
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(chart, "chart");
        Objects.requireNonNull(suppliedChart, "suppliedChart");
    }

    /** A turn for the owner's own chart, with no second person involved. */
    public static TurnContext of(long userId, String senderE164, String question, NatalChartBundle chart) {
        return new TurnContext(userId, senderE164, question, chart, Optional.empty());
    }

    /** The chart a tool should judge: the supplied one when there is one, else the owner's. */
    public NatalChart chartToJudge() {
        return suppliedChart.orElseGet(chart::chart);
    }
}
