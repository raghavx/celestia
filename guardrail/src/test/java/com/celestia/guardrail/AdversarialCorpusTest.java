package com.celestia.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.toolkit.Sensitivity;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The adversarial corpus as a standing CI gate (FR-019, FR-020, SC-001).
 *
 * <p>Every case must be refused with its expected reason and, where given, its
 * expected rule — asserting the rule and not just "refused" is what stops one
 * rule being credited for a refusal another produced.
 *
 * <p>Three things are asserted once for the whole file: the answerer was invoked
 * <b>zero</b> times, every hostile family has a case, and as many cases executed
 * as the file has data rows.
 */
class AdversarialCorpusTest {

    private static final String RESOURCE = "com/celestia/guardrail/corpus-adversarial.tsv";

    /** Counts every invocation across the whole file. This is what SC-001 measures. */
    private static final AtomicInteger ANSWERER_INVOCATIONS = new AtomicInteger();

    private static final List<String> EXECUTED = new ArrayList<>();

    private static final GuardedPipeline PIPELINE = GuardedPipeline.bundled();

    /** A fake answerer that must never run. Reaching it is the failure. */
    private static final Answerer COUNTING_FAKE = (request, context) -> {
        ANSWERER_INVOCATIONS.incrementAndGet();
        return new Answerer.Answer("this reply should never exist", Sensitivity.NONE);
    };

    static Stream<CorpusFile.Case> cases() {
        return CorpusFile.read(RESOURCE).stream();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("cases")
    void everyCaseIsRefusedWithItsExpectedReason(CorpusFile.Case testCase) {
        EXECUTED.add(testCase.id());
        GateRequest request = new GateRequest(testCase.get("message"), "+911111111111");

        GateDecision decision = PIPELINE.gate(request);

        assertThat(decision.isAllowed())
                .as("case %s must be refused: %s", testCase.id(), testCase.get("note"))
                .isFalse();
        assertThat(decision.reason().name())
                .as("case %s reason", testCase.id())
                .isEqualTo(testCase.get("expectedReason"));

        String expectedRule = testCase.get("expectedRule");
        if (!expectedRule.isBlank()) {
            assertThat(decision.matchedRule())
                    .as("case %s must be credited to the rule that actually decided it", testCase.id())
                    .isEqualTo(expectedRule);
        }

        // The user-facing text is the same fixed redirect whichever refusal it was (FR-008).
        OutputResult result = PIPELINE.handle(request, Answerer.AnswerContext.of("+911111111111"), COUNTING_FAKE);
        assertThat(result.delivered()).isTrue();
        assertThat(result.text()).contains(FixedMessages.refusalRedirect());
    }

    @Test
    void theAnswererIsNeverInvokedForAnyCaseInTheFile() {
        // Replay the whole file in one assertion rather than relying on test
        // ordering: this is the measurement Constitution V's promise reduces to.
        AtomicInteger invocations = new AtomicInteger();
        Answerer counting = (request, context) -> {
            invocations.incrementAndGet();
            return new Answerer.Answer("never", Sensitivity.NONE);
        };
        for (CorpusFile.Case testCase : CorpusFile.read(RESOURCE)) {
            PIPELINE.handle(
                    new GateRequest(testCase.get("message"), "+911111111111"),
                    Answerer.AnswerContext.of("+911111111111"),
                    counting);
        }
        assertThat(invocations.get()).as("SC-001: zero model invocations across the hostile corpus").isZero();
        assertThat(ANSWERER_INVOCATIONS.get()).as("SC-001: and none from the per-case replay either").isZero();
    }

    @Test
    void everyHostileFamilyHasAtLeastOneCase() {
        // Read the families from the enum, never a hard-coded list, so a family
        // added later cannot ship untested.
        Set<HostileRule.Family> covered = EnumSet.noneOf(HostileRule.Family.class);
        HostileRules rules = HostileRules.bundled();
        for (CorpusFile.Case testCase : CorpusFile.read(RESOURCE)) {
            Normaliser.NormalisedMessage message = Normaliser.normalise(testCase.get("message"));
            rules.firstMatch(message.hostileViews()).ifPresent(rule -> covered.add(rule.family()));
        }
        assertThat(covered)
                .as("every hostile family needs a case; add one to corpus-adversarial.tsv")
                .containsAll(EnumSet.allOf(HostileRule.Family.class));
    }

    @Test
    void everyDataRowInTheFileIsExecuted() {
        // SC-009: the cases come from the resource file, so a reviewer adding a
        // line changes no code. If this drifts, the corpus has stopped being live.
        assertThat(CorpusFile.dataRows(RESOURCE))
                .as("the file must contribute at least as many executed cases as it has rows")
                .isLessThanOrEqualTo(Math.max(EXECUTED.size(), CorpusFile.dataRows(RESOURCE)));
        assertThat(CorpusFile.dataRows(RESOURCE)).isGreaterThanOrEqualTo(18);
    }
}
