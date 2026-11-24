package com.celestia.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.toolkit.Sensitivity;
import com.celestia.guardrail.Answerer;
import com.celestia.guardrail.FixedMessages;
import com.celestia.guardrail.GateRequest;
import com.celestia.guardrail.GuardedPipeline;
import com.celestia.guardrail.OutputResult;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SPEC-012 FR-018a, FR-018b and SC-012 (T028a) — <b>both directions</b>.
 *
 * <p>A question that cannot be understood on its own is answered with a question.
 * A question that names its own matter is answered as it stands. The second is
 * what stops the first from being satisfied by a model that asks "what do you
 * mean?" at everything — the same trap the decline outcomes close for SC-007.
 *
 * <p><b>What a stub model cannot prove</b> is that the model makes the right
 * choice; that is a prompt property, and SC-012 is finally judged on the
 * evaluation set. What is proved here is that the prompt carries both
 * instructions, that a clarifying question is delivered intact rather than
 * mistaken for a refusal, and that no turn is composed against an earlier one.
 */
class AsksRatherThanGuessesTest {

    private static OutputResult deliver(String draft) {
        return GuardedPipeline.bundled().handle(
                new GateRequest("when will I get married?", TurnFixtures.OWNER),
                Answerer.AnswerContext.of(TurnFixtures.OWNER),
                (request, context) -> new Answerer.Answer(draft, Sensitivity.NONE));
    }

    @Test
    @DisplayName("a clarifying question is delivered as written, not withheld or redirected")
    void aClarifyingQuestionReachesTheOwner() {
        String asking = "Whose chart should I look at — do you mean your sister, or someone else?";

        OutputResult result = deliver(asking);

        assertThat(result.delivered()).isTrue();
        assertThat(result.text()).isEqualTo(asking);
        assertThat(result.text()).isNotEqualTo(FixedMessages.refusalRedirect());
    }

    @Test
    @DisplayName("the prompt tells the model to ask rather than guess (FR-018b)")
    void thePromptCarriesTheInstructionToAsk() {
        String prompt = new SystemPrompt().text().toLowerCase(Locale.ROOT);
        assertThat(prompt).contains("ask what is meant");
        assertThat(prompt).contains("do not guess");
        assertThat(prompt).contains("what about her?");
    }

    @Test
    @DisplayName("and tells it NOT to ask when the question names its own matter (FR-018a)")
    void thePromptCarriesTheOtherDirectionToo() {
        // Without this, a model that asks for clarification on everything would
        // satisfy FR-018b perfectly and be useless.
        String prompt = new SystemPrompt().text().toLowerCase(Locale.ROOT);
        assertThat(prompt).contains("and what about my career?");
        assertThat(prompt).contains("ask only when the subject is genuinely missing");
    }

    @Test
    @DisplayName("a question naming its own matter is answered, with the chart already as context")
    void aQuestionNamingItsMatterIsAnswered() {
        // It works because the owner's chart is already in front of the model
        // (FR-009), not because anything was remembered.
        AnswererHarness harness = AnswererHarness.standard();
        harness.model.thenAnswer("Your 10th cusp sub lord rules the work you are drawn to.");

        AnswererHarness.Outcome outcome = harness.answer("and what about my career?");

        assertThat(outcome.answer().draft()).contains("10th cusp sub lord");
        String seen = harness.model.everythingSeen();
        assertThat(seen).contains("and what about my career?");
        assertThat(seen)
                .as("the chart is supplied as context, so the matter in the message is enough")
                .contains("Their chart, already cast")
                .contains("cusp 10");
    }

    @Test
    @DisplayName("no reply is composed against context from an earlier turn, since none is kept")
    void nothingIsCarriedBetweenTurns() {
        AnswererHarness harness = AnswererHarness.standard();
        harness.model.thenAnswer("First answer about marriage.").thenAnswer("Second answer about work.");

        harness.answer("when will I get married?");
        harness.answer("and what about my career?");

        String second = harness.model.promptsSeen().getLast().getInstructions().stream()
                .map(message -> String.valueOf(message.getText()))
                .reduce("", (a, b) -> a + "\n" + b);

        assertThat(second).doesNotContain("when will I get married?");
        assertThat(second).doesNotContain("First answer about marriage");
    }
}
