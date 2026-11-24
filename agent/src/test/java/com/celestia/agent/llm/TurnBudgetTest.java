package com.celestia.agent.llm;

import static com.celestia.agent.llm.StubChatModel.toolCall;
import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.agent.AgentMessages;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.AnthropicChatOptions;

/**
 * SPEC-012 T045 / FR-010 — <b>the deadline is a bound, not a checkpoint.</b>
 *
 * <p>The defect this closes was found by a model too slow to hide it: with
 * {@code turn-deadline: PT120S}, turns ran <b>24 minutes</b>. Two causes, and
 * only one of them was obvious.
 *
 * <ol>
 *   <li>Spring AI retries <b>ten times</b> by default, backing off from 2s with a
 *       multiplier of 5 and no maximum interval. Nobody chose that, and it alone
 *       exceeds any sane budget.
 *   <li>The deadline was only checked <em>between</em> model calls. A single
 *       blocking call can outlive any number of checks that happen before and
 *       after it, so checking was never going to be enough.
 *  </ol>
 *
 * <p>The fix is that each call is given <b>only the time the turn has left</b>, so
 * however many rounds it takes, their timeouts cannot sum past the deadline. That
 * is what these tests assert — on the provider that supports a per-request
 * timeout, and on the scope arithmetic that feeds it.
 */
class TurnBudgetTest {

    private static final List<org.springframework.ai.tool.ToolCallback> NO_TOOLS = List.of();

    @Test
    @DisplayName("the budget is spent down, not handed out afresh each round")
    void remainingShrinksAsTheTurnProceeds() {
        var clock = TurnFixtures.tickingClock(); // +1 hour per reading
        TurnScope scope = new TurnScope(
                TurnFixtures.context("when will I get married?"), clock.instant(),
                Duration.ofHours(3), 6);

        Duration first = scope.remaining(clock.instant());
        Duration second = scope.remaining(clock.instant());

        assertThat(first).isLessThanOrEqualTo(Duration.ofHours(3));
        assertThat(second)
                .as("a later round must get less, or rounds could sum past the deadline")
                .isLessThan(first);
    }

    @Test
    @DisplayName("a turn already past its deadline has zero left, never a negative budget")
    void remainingNeverGoesNegative() {
        // A negative timeout would be rejected by the client, or worse, read as
        // "no timeout". Zero is the honest answer and tells the caller not to
        // start another call.
        var clock = TurnFixtures.tickingClock();
        TurnScope scope = new TurnScope(
                TurnFixtures.context("q"), clock.instant(), Duration.ofSeconds(1), 6);

        assertThat(scope.remaining(clock.instant())).isZero();
    }

    @Test
    @DisplayName("the Anthropic provider puts the remaining budget on the request, and no retries")
    void theRequestCarriesTheRemainingBudget() {
        TurnOptions options = new AnthropicTurnOptions(TurnFixtures.anthropicProperties());

        var built = (AnthropicChatOptions) options.forModel("claude-sonnet-5", NO_TOOLS, Duration.ofSeconds(42));

        assertThat(built.getTimeout())
                .as("the per-request timeout IS the turn's remaining budget — that is the bound")
                .isEqualTo(Duration.ofSeconds(42));
        assertThat(built.getMaxRetries())
                .as("ADR-0009 makes the fallback model the retry policy; a retry underneath it "
                        + "competes with the design and multiplies the latency")
                .isZero();
    }

    @Test
    @DisplayName("a shrinking budget produces a shrinking request timeout")
    void theTimeoutShrinksWithTheBudget() {
        TurnOptions options = new AnthropicTurnOptions(TurnFixtures.anthropicProperties());

        var early = (AnthropicChatOptions) options.forModel("m", NO_TOOLS, Duration.ofSeconds(60));
        var late = (AnthropicChatOptions) options.forModel("m", NO_TOOLS, Duration.ofSeconds(5));

        assertThat(late.getTimeout()).isLessThan(early.getTimeout());
    }

    @Test
    @DisplayName("the Ollama provider is bounded by the HTTP read timeout, and says so")
    void theOllamaProviderIgnoresTheBudgetDeliberately() {
        // Its options carry no per-request timeout, so the bound comes from
        // spring.http.clients.read-timeout instead. Asserted so the difference is
        // a known property of this provider rather than a surprise.
        TurnOptions options = new OllamaTurnOptions(TurnFixtures.ollamaProperties());

        var built = options.forModel("qwen2.5:0.5b", NO_TOOLS, Duration.ofSeconds(42));

        assertThat(built).isNotInstanceOf(AnthropicChatOptions.class);
        assertThat(built.getModel()).isEqualTo("qwen2.5:0.5b");
    }

    @Test
    @DisplayName("an expired turn produces the fixed message without reaching the model")
    void anExpiredTurnNeverCallsTheModel() {
        // The end-to-end half: whatever the client does, a turn past its deadline
        // ends in a fixed message and a recorded TIMEOUT rather than a long wait.
        AnswererHarness harness = AnswererHarness.alreadyExpired();
        harness.model.thenAnswer("never reached");

        AnswererHarness.Outcome outcome = harness.answer("when will I get married?");

        assertThat(outcome.answer().draft()).isEqualTo(AgentMessages.COULD_NOT_COMPLETE);
        assertThat(outcome.scope().fault()).contains(TurnDetail.FaultKind.TIMEOUT);
        assertThat(harness.model.callCount()).isZero();
    }

    @Test
    @DisplayName("the round cap still ends a turn that converges on nothing")
    void theRoundCapIsUnaffected() {
        // The two bounds remain separate: a round-cap hit is a prompt defect and a
        // timeout is a hosting one (contracts/answerer-api.md).
        AnswererHarness harness = AnswererHarness.withRoundCap(2);
        harness.model.thenLoopOn("getChartPromises", "{\"house\":7}", 5).thenAnswer("never reached");

        AnswererHarness.Outcome outcome = harness.answer("when will I get married?");

        assertThat(outcome.scope().fault()).contains(TurnDetail.FaultKind.ROUND_CAP);
    }
}
