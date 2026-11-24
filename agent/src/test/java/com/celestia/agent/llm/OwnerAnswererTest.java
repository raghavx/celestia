package com.celestia.agent.llm;

import static com.celestia.agent.llm.StubChatModel.toolCall;
import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.agent.AgentMessages;
import com.celestia.core.toolkit.Sensitivity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SPEC-012 T019 — the answerer, against a stub model.
 *
 * <p>The tool loop, the two bounds, the fallback switch and the "never throw"
 * promise. None of it needs Ollama, and none of it would be proved by a real
 * model answering one question correctly.
 */
class OwnerAnswererTest {

    @Test
    @DisplayName("a tool loop that converges returns the model's draft")
    void aConvergingToolLoop() {
        AnswererHarness harness = AnswererHarness.standard();
        harness.model
                .thenCallTools(toolCall("getChartPromises", "{\"house\":7}"))
                .thenAnswer("The 7th cusp sub lord connects 2, 7 and 11.");

        AnswererHarness.Outcome outcome = harness.answer("when will I get married?");

        assertThat(outcome.answer().draft()).contains("7th cusp sub lord");
        assertThat(outcome.scope().rounds()).isEqualTo(1);
        assertThat(outcome.scope().toolCalls()).singleElement()
                .satisfies(call -> assertThat(call.tool()).isEqualTo("getChartPromises"));
    }

    @Test
    @DisplayName("the round cap ends the turn as ROUND_CAP — a prompt defect")
    void theRoundCapEndsTheTurn() {
        AnswererHarness harness = AnswererHarness.withRoundCap(2);
        harness.model.thenLoopOn("getChartPromises", "{\"house\":7}", 5).thenAnswer("never reached");

        AnswererHarness.Outcome outcome = harness.answer("when will I get married?");

        assertThat(outcome.answer().draft()).isEqualTo(AgentMessages.COULD_NOT_COMPLETE);
        assertThat(outcome.scope().fault()).contains(TurnDetail.FaultKind.ROUND_CAP);
        assertThat(outcome.scope().rounds()).isEqualTo(3); // the one past the cap is what trips it
    }

    @Test
    @DisplayName("the deadline ends the turn as TIMEOUT — a hosting problem, counted apart")
    void theDeadlineEndsTheTurn() {
        AnswererHarness harness = AnswererHarness.alreadyExpired();
        harness.model.thenAnswer("never reached");

        AnswererHarness.Outcome outcome = harness.answer("when will I get married?");

        assertThat(outcome.answer().draft()).isEqualTo(AgentMessages.COULD_NOT_COMPLETE);
        assertThat(outcome.scope().fault()).contains(TurnDetail.FaultKind.TIMEOUT);
        assertThat(harness.model.callCount())
                .as("an expired turn must not reach the model at all")
                .isZero();
    }

    @Test
    @DisplayName("unreadable tool JSON switches to the fallback — the normal path on a small model")
    void malformedToolJsonSwitchesToTheFallback() {
        AnswererHarness harness = AnswererHarness.standard();
        harness.model
                .thenFail(new IllegalStateException("could not deserialize tool call arguments"))
                .thenAnswer("Saturn in the 7th delays rather than denies.");

        AnswererHarness.Outcome outcome = harness.answer("when will I get married?");

        assertThat(outcome.answer().draft()).contains("delays rather than denies");
        assertThat(outcome.scope().modelUsed()).contains("fallback-model");
        assertThat(outcome.scope().fallbackSwitched()).isTrue();
        assertThat(outcome.scope().fault())
                .as("a fallback that answered is not a fault")
                .isEmpty();
    }

    @Test
    @DisplayName("both models failing is a fault, and still a fixed message rather than an exception")
    void bothModelsFailing() {
        AnswererHarness harness = AnswererHarness.standard();
        harness.model
                .thenFail(new IllegalStateException("connection refused"))
                .thenFail(new IllegalStateException("connection refused"));

        AnswererHarness.Outcome outcome = harness.answer("when will I get married?");

        assertThat(outcome.answer().draft()).isEqualTo(AgentMessages.COULD_NOT_COMPLETE);
        assertThat(outcome.scope().fault()).contains(TurnDetail.FaultKind.MODEL_UNREACHABLE);
    }

    @Test
    @DisplayName("sensitivityUsed is the maximum over the results used, not read from the draft")
    void sensitivityIsReportedNotInferred() {
        // SPEC-011 FR-014: the advisory must follow content, not wording. A draft
        // that says nothing about health still carries HEALTH if a health rule was
        // what grounded it.
        AnswererHarness harness = AnswererHarness.standard();
        harness.model
                .thenCallTools(toolCall("getToolkitLookup", "{\"point\":\"SATURN\",\"table\":\"disease\"}"))
                .thenAnswer("A quiet period, nothing more.");

        AnswererHarness.Outcome outcome = harness.answer("how is my health?");

        assertThat(outcome.answer().sensitivityUsed()).isEqualTo(Sensitivity.HEALTH);
        assertThat(outcome.answer().draft()).doesNotContain("health", "doctor");
    }

    @Test
    @DisplayName("a fault's fixed message carries no sensitivity, so no advisory is appended to it")
    void aFaultCarriesNoSensitivity() {
        AnswererHarness harness = AnswererHarness.standard();
        harness.model
                .thenFail(new IllegalStateException("connection refused"))
                .thenFail(new IllegalStateException("connection refused"));

        assertThat(harness.answer("how is my health?").answer().sensitivityUsed())
                .isEqualTo(Sensitivity.NONE);
    }

    @Test
    @DisplayName("an empty draft is treated as a failure rather than delivered as silence")
    void anEmptyDraftIsAFailure() {
        AnswererHarness harness = AnswererHarness.standard();
        harness.model.thenAnswer("   ").thenAnswer("   ");

        assertThat(harness.answer("when will I get married?").answer().draft())
                .isEqualTo(AgentMessages.COULD_NOT_COMPLETE);
    }
}
