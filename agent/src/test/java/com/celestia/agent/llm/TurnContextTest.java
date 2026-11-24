package com.celestia.agent.llm;

import static com.celestia.agent.llm.StubChatModel.toolCall;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.agent.persistence.json.PersistenceJson;
import com.celestia.core.chart.NatalChart;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SPEC-012 FR-009 and FR-011 (T032) — the chart is ready before the model runs,
 * and a second person's chart dies with the turn.
 */
class TurnContextTest {

    @Test
    @DisplayName("the chart and the running dasha are in hand before the model is called")
    void theChartIsPreFetched() {
        AnswererHarness harness = AnswererHarness.standard();
        harness.model.thenAnswer("An answer.");

        harness.answer("when will I get married?");

        // Supplied as context, not fetched by the model: the first thing the model
        // sees is the cusps and the periods running now.
        String seen = harness.model.everythingSeen();
        assertThat(seen).contains("Their chart, already cast");
        assertThat(seen).contains("cusp 1:").contains("cusp 7:").contains("cusp 12:");
        assertThat(seen).contains("Periods running now:").contains("MAHADASHA");
        assertThat(harness.model.callCount())
                .as("the context was assembled before the single model call")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("the model's tool calls are about judgement, not about recomputing the basics")
    void theToolsAreForJudgement() {
        AnswererHarness harness = AnswererHarness.standard();
        harness.model
                .thenCallTools(toolCall("getChartPromises", "{\"house\":7}"))
                .thenAnswer("The 7th cusp sub lord connects 2, 7 and 11.");

        AnswererHarness.Outcome outcome = harness.answer("when will I get married?");

        assertThat(outcome.scope().toolkitCallCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a turn for the owner's own chart has no supplied chart")
    void noSuppliedChartByDefault() {
        assertThat(TurnFixtures.context("when will I get married?").suppliedChart()).isEmpty();
    }

    @Test
    @DisplayName("a supplied chart is what the tools judge for that turn, and only that turn")
    void aSuppliedChartIsUsedForTheTurn() {
        NatalChart ownChart = TurnFixtures.bundle().chart();
        TurnContext supplied = new TurnContext(
                1L, TurnFixtures.OWNER, "will she marry a doctor?",
                TurnFixtures.bundle(), Optional.of(ownChart));

        assertThat(supplied.chartToJudge()).isSameAs(ownChart);
        assertThat(supplied.suppliedChart()).isPresent();
        // And the owner's own bundle is still there for the periods, untouched.
        assertThat(supplied.chart()).isSameAs(TurnFixtures.bundle());
    }

    @Test
    @DisplayName("a second person's birth details are redacted from the turn record (FR-011)")
    void suppliedBirthDataIsNeverPersisted() {
        // The turn record is persisted, so recording the arguments of castChartFor
        // verbatim would store exactly what FR-011 forbids storing.
        TurnScope scope = TurnFixtures.scope("will she marry a doctor?");
        scope.beginCall("castChartFor",
                "{\"birthDate\":\"1991-02-03\",\"birthTime\":\"06:15\",\"birthPlace\":\"Nagpur, India\"}");

        String recorded = PersistenceJson.mapper().writeValueAsString(scope.toolCalls());

        assertThat(recorded).doesNotContain("1991-02-03", "06:15", "Nagpur");
        assertThat(recorded).contains("redacted");
    }

    @Test
    @DisplayName("an ordinary tool's arguments are recorded as the model supplied them")
    void otherToolArgumentsAreKept() {
        TurnScope scope = TurnFixtures.scope("when will I get married?");
        scope.beginCall("getChartPromises", "{\"house\":7}");
        assertThat(scope.toolCalls().getFirst().arguments()).isEqualTo("{\"house\":7}");
    }

    @Test
    @DisplayName("a tool reached outside a turn fails loudly rather than inventing an identity")
    void noIdentityWithoutATurn() {
        // Which is what makes "no @Tool method takes a userId" safe: there is
        // exactly one place an identity can come from, and it is the open turn.
        assertThat(TurnScope.isActive()).isFalse();
        assertThatThrownBy(TurnScope::current)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no turn in progress");
    }

    @Test
    @DisplayName("the scope is gone after the turn, so nothing of it can reach the next one")
    void theScopeDoesNotOutliveTheTurn() {
        TurnScope scope = TurnFixtures.scope("when will I get married?");
        scope.open();
        assertThat(TurnScope.isActive()).isTrue();
        scope.close();
        assertThat(TurnScope.isActive()).isFalse();
    }
}
