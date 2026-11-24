package com.celestia.agent.llm;

import static com.celestia.agent.llm.StubChatModel.toolCall;
import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.toolkit.Sensitivity;
import com.celestia.guardrail.Answerer;
import com.celestia.guardrail.FixedMessages;
import com.celestia.guardrail.GateRequest;
import com.celestia.guardrail.GuardedPipeline;
import com.celestia.guardrail.OutputResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SPEC-012 FR-014, SC-002 and SC-007 (T028, T029) — the agent's silence is as
 * reviewable as its answers.
 *
 * <p>Three different things are easy to confuse, and the record has to keep them
 * apart: the marker that means "not astrology at all", a decline in the agent's
 * own voice, and an answer that asserted KP judgement with nothing behind it.
 *
 * <p><b>What this cannot test</b> is whether the model chooses the right one. That
 * is a prompt property, and only a practitioner reading the evaluation set can
 * judge it (SC-001). What it can test is that each choice lands where it should.
 */
class NoBasisTest {

    private static OutputResult through(GuardedPipeline pipeline, String draft, Sensitivity sensitivity) {
        Answerer answerer = (request, context) -> new Answerer.Answer(draft, sensitivity);
        return pipeline.handle(
                new GateRequest("when will I get married?", TurnFixtures.OWNER),
                Answerer.AnswerContext.of(TurnFixtures.OWNER),
                answerer);
    }

    @Test
    @DisplayName("the out-of-scope marker is replaced in full by the fixed redirect")
    void theMarkerIsReplacedInFull() {
        // Terminal, and nothing of the draft survives it — the owner must not see
        // the marker, and must not see half an answer either.
        OutputResult result = through(GuardedPipeline.bundled(), "<<OUT_OF_SCOPE>>", Sensitivity.NONE);

        assertThat(result.delivered()).isTrue();
        assertThat(result.text()).isEqualTo(FixedMessages.refusalRedirect());
        assertThat(result.text()).doesNotContain("OUT_OF_SCOPE");
    }

    @Test
    @DisplayName("a decline written in prose is delivered as the agent wrote it")
    void aProseDeclineIsDelivered() {
        // FR-014's "says so in its own voice": the product's fixed redirect is for
        // a question outside astrology, not for one the chart cannot answer.
        String decline = "I can't read that from your chart — the cusp gives me nothing to judge it by. "
                + "I can speak to marriage timing or your work, if either would help.";

        OutputResult result = through(GuardedPipeline.bundled(), decline, Sensitivity.NONE);

        assertThat(result.text()).isEqualTo(decline);
        assertThat(result.text()).isNotEqualTo(FixedMessages.refusalRedirect());
    }

    @Test
    @DisplayName("an answer with no toolkit call behind it is visible as ungrounded (SC-002)")
    void anUngroundedAnswerIsVisibleInTheRecord() {
        // The model answered from its own training: engine numbers but no toolkit
        // judgement. Constitution X calls that ungrounded, and the turn record is
        // what makes it countable rather than invisible.
        AnswererHarness harness = AnswererHarness.standard();
        harness.model
                .thenCallTools(toolCall("getNatalChart", "{}"))
                .thenAnswer("Jupiter in the seventh means marriage in 2027.");

        AnswererHarness.Outcome outcome = harness.answer("when will I get married?");

        assertThat(outcome.scope().toolCalls()).hasSize(1);
        assertThat(outcome.scope().toolkitCallCount())
                .as("an engine call grounds no interpretation")
                .isZero();
    }

    @Test
    @DisplayName("an answer resting on a toolkit rule is grounded, and counted as such")
    void aGroundedAnswerIsCountedApart() {
        AnswererHarness harness = AnswererHarness.standard();
        harness.model
                .thenCallTools(
                        toolCall("getNatalChart", "{}"),
                        toolCall("findToolkitRules", "{\"matter\":\"marriage\"}"))
                .thenAnswer("The 7th cusp sub lord connects 2, 7 and 11.");

        AnswererHarness.Outcome outcome = harness.answer("when will I get married?");

        assertThat(outcome.scope().toolkitCallCount()).isEqualTo(1);
        assertThat(outcome.scope().references().size())
                .as("a grounded answer has sources behind it, resolvable for review")
                .isPositive();
    }

    @Test
    @DisplayName("the four outcomes are distinct values, so SC-007 can count the directions apart")
    void theOutcomesKeepTheDirectionsApart() {
        // A product that declined everything would pass an "invents nothing" test
        // perfectly. SC-007 counts both directions, which is only possible because
        // a decline, a redirect, a gate refusal and an answer are four values and
        // not one.
        assertThat(TurnDetail.Outcome.values())
                .contains(
                        TurnDetail.Outcome.ANSWERED,
                        TurnDetail.Outcome.AGENT_NO_BASIS,
                        TurnDetail.Outcome.AGENT_REDIRECT,
                        TurnDetail.Outcome.GATE_REFUSED);
        assertThat(TurnDetail.Outcome.AGENT_NO_BASIS).isNotEqualTo(TurnDetail.Outcome.GATE_REFUSED);
        assertThat(TurnDetail.Outcome.AGENT_REDIRECT).isNotEqualTo(TurnDetail.Outcome.GATE_REFUSED);
    }
}
