package com.celestia.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * SC-008: the gate adds no delay a user could notice.
 *
 * <p>Asserts plan.md's Performance Goal of <b>well under 1 ms</b> rather than
 * SC-008's much looser "tenth of a second", so a hundredfold regression fails CI
 * instead of passing it. The mean over many calls, not a single call, so one GC
 * pause does not fail the build.
 */
class GatePerformanceTest {

    private static final int WARMUP = 2_000;
    private static final int RUNS = 1_000;
    private static final Duration BUDGET = Duration.ofMillis(1);

    @Test
    void aDecisionOnAMessageAtTheBoundIsWellUnderOneMillisecond() {
        Gate gate = Gate.bundled();
        String atBound = "will i get married and when will the dasha change? "
                + "x".repeat(Gate.MAX_MESSAGE_LENGTH - 60);
        GateRequest request = new GateRequest(atBound, "+911111111111");
        assertThat(atBound.length()).isLessThanOrEqualTo(Gate.MAX_MESSAGE_LENGTH);

        for (int i = 0; i < WARMUP; i++) {
            gate.decide(request);
        }

        long start = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            gate.decide(request);
        }
        long meanNanos = (System.nanoTime() - start) / RUNS;

        assertThat(Duration.ofNanos(meanNanos))
                .as("mean decision time over %s calls at the %s-character bound", RUNS, Gate.MAX_MESSAGE_LENGTH)
                .isLessThan(BUDGET);
    }

    @Test
    void theVocabularyIsBuiltOncePerGateNotPerMessage() {
        // Building it per message would be the obvious way to blow the budget.
        long start = System.nanoTime();
        Gate gate = Gate.bundled();
        long buildNanos = System.nanoTime() - start;

        GateRequest request = new GateRequest("when will i get married?", "+911111111111");
        for (int i = 0; i < WARMUP; i++) {
            gate.decide(request);
        }
        start = System.nanoTime();
        for (int i = 0; i < RUNS; i++) {
            gate.decide(request);
        }
        long decideNanos = (System.nanoTime() - start) / RUNS;

        assertThat(decideNanos)
                .as("one decision must be far cheaper than building the vocabulary")
                .isLessThan(buildNanos / 10);
    }
}
