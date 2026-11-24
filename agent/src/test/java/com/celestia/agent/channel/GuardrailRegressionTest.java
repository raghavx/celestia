package com.celestia.agent.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.celestia.agent.entitlement.EntitlementProperties;
import com.celestia.agent.entitlement.EntitlementService;
import com.celestia.agent.llm.LlmProperties;
import com.celestia.agent.llm.SystemPrompt;
import com.celestia.agent.llm.TurnFixtures;
import com.celestia.agent.persistence.store.AppUserRef;
import com.celestia.agent.persistence.store.BirthDataStore;
import com.celestia.agent.persistence.store.ChartCacheService;
import com.celestia.agent.persistence.store.TurnRecordStore;
import com.celestia.channel.whatsapp.InboundMessage;
import com.celestia.channel.whatsapp.MessageContent;
import com.celestia.guardrail.Answerer;
import com.celestia.guardrail.GuardedPipeline;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SPEC-012 SC-006 — <b>the guardrail did not regress when a real model appeared.</b>
 *
 * <p>SPEC-011 proved zero model invocations against a <i>fake</i> answerer, which
 * was the best proof available before a model existed and says nothing about
 * whether wiring a real one introduced a bypass. This feature adds two things in
 * front of the gate (entitlement, the permit) and one behind it (the output
 * stage); any of them could have.
 *
 * <p>So the corpus is replayed through the <b>real reply path</b> —
 * {@link GuardedReplyHandler} with the real {@link GuardedPipeline} — wrapped in a
 * counting decorator at the {@code Answerer} boundary, asserting zero invocations
 * across the whole file.
 *
 * <p><b>No model is needed</b>: the assertion is that nothing was called, so this
 * belongs in the default build (research.md §11).
 *
 * <p>The corpus file itself is reused, never copied. It lives in {@code guardrail}'s
 * test resources, which are not on this module's classpath, so it is read from the
 * source tree — and the test fails if it cannot be found, because a regression
 * test that silently skips is worse than none.
 */
class GuardrailRegressionTest {

    private static final Path CORPUS = Path.of(
            "..", "guardrail", "src", "test", "resources", "com", "celestia", "guardrail",
            "corpus-adversarial.tsv");

    private static final String OWNER = TurnFixtures.OWNER;

    /** id and message, the only two columns this test needs. */
    private static List<String[]> corpus() {
        assertThat(CORPUS)
                .as("SPEC-011's corpus must be where this test expects it; do not copy the file")
                .exists();
        List<String[]> cases = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(CORPUS, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("id\t")) {
                    continue;
                }
                String[] columns = line.split("\t", -1);
                cases.add(new String[] {columns[0], columns[1]});
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return cases;
    }

    @Test
    @DisplayName("the whole adversarial corpus, through the real path, invokes the model zero times")
    void zeroInvocationsAcrossTheWholeCorpus() {
        List<String[]> cases = corpus();
        assertThat(cases)
                .as("an empty corpus would make this test pass vacuously")
                .hasSizeGreaterThan(10);

        AtomicInteger invocations = new AtomicInteger();
        GuardedReplyHandler handler = handlerCounting(invocations, OWNER);
        AppUserRef owner = new AppUserRef(1L, OWNER, false);

        List<String> reached = new ArrayList<>();
        for (String[] testCase : cases) {
            int before = invocations.get();
            handler.reply(owner, message(OWNER, testCase[1]));
            if (invocations.get() > before) {
                reached.add(testCase[0]);
            }
        }

        assertThat(reached).as("cases that reached the answerer").isEmpty();
        assertThat(invocations).hasValue(0);
    }

    @Test
    @DisplayName("and zero invocations for an unentitled sender, whatever they send (SC-005)")
    void zeroInvocationsForAStranger() {
        AtomicInteger invocations = new AtomicInteger();
        // An allow-list that does not hold this sender: entitlement stops every
        // message before the gate even sees it.
        GuardedReplyHandler handler = handlerCounting(invocations, "+10000000000");
        AppUserRef stranger = new AppUserRef(2L, OWNER, true);

        for (String[] testCase : corpus()) {
            handler.reply(stranger, message(OWNER, testCase[1]));
        }
        assertThat(invocations).hasValue(0);
    }

    private static InboundMessage message(String from, String body) {
        return new InboundMessage("wamid." + Math.abs(body.hashCode()), from,
                Instant.parse("2026-11-24T08:00:00Z"), new MessageContent.TextContent(body));
    }

    private static GuardedReplyHandler handlerCounting(AtomicInteger invocations, String... allowList) {
        ChartCacheService charts = mock(ChartCacheService.class);
        BirthDataStore births = mock(BirthDataStore.class);
        when(charts.natalChart(anyLong())).thenReturn(TurnFixtures.bundle());
        when(births.load(anyLong())).thenReturn(Optional.of(TurnFixtures.storedBirth()));

        Answerer counting = (request, context) -> {
            invocations.incrementAndGet();
            return new Answerer.Answer("this must never be produced", com.celestia.core.toolkit.Sensitivity.NONE);
        };

        return new GuardedReplyHandler(
                new EntitlementService(new EntitlementProperties(List.of(allowList))),
                new Semaphore(1),
                charts,
                births,
                GuardedPipeline.bundled(),
                counting,
                mock(TurnRecordStore.class),
                new SystemPrompt(),
                new LlmProperties(LlmProperties.OLLAMA, "http://localhost:11434", "m", "f", "5m", 6,
                        Duration.ofMinutes(5), 1, 16000, "", ""),
                TurnFixtures.clock(),
                new SimpleMeterRegistry());
    }
}
