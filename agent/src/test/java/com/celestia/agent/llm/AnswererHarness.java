package com.celestia.agent.llm;

import com.celestia.agent.llm.tools.EngineTools;
import com.celestia.agent.llm.tools.ToolkitTools;
import com.celestia.core.horary.HoraryChartFactory;
import com.celestia.core.judgement.RulingPlanetsFactory;
import com.celestia.core.toolkit.KpToolkit;
import com.celestia.core.toolkit.PromiseEvaluator;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHoraryHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisSunriseProvider;
import com.celestia.guardrail.Answerer;
import com.celestia.guardrail.GateRequest;
import java.time.Clock;
import java.time.Duration;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;

/**
 * An {@link OwnerAnswerer} over a stub model, with the <b>real</b> tools, the real
 * reference swap and the real bounded tool-calling manager behind it.
 *
 * <p>Only the model is a stand-in. That is deliberate: it is the one part whose
 * behaviour no test can pin, and everything else is what the stub-model tier
 * exists to prove.
 */
public final class AnswererHarness {

    public final StubChatModel model;
    public final OwnerAnswerer answerer;

    private final Duration deadline;
    private final int maxRounds;
    private final Clock clock;

    private AnswererHarness(Duration deadline, int maxRounds, Clock clock) {
        this.deadline = deadline;
        this.maxRounds = maxRounds;
        this.clock = clock;

        SwissEphemerisConfig config = SwissEphemerisConfig.resolve();
        var positions = new SwissEphemerisPositionProvider(config);
        var houses = new SwissEphemerisHouseProvider(config);
        EngineTools engineTools = new EngineTools(
                new RulingPlanetsFactory(positions, houses, new SwissEphemerisSunriseProvider(config)),
                new HoraryChartFactory(positions, new SwissEphemerisHoraryHouseProvider(config)),
                clock);
        KpToolkit toolkit = KpToolkit.bundled();
        ToolkitTools toolkitTools = new ToolkitTools(toolkit, new PromiseEvaluator(toolkit));

        ToolCallingManager manager =
                new TurnToolCallingManager(DefaultToolCallingManager.builder().build(), clock);
        this.model = new StubChatModel(manager);
        LlmProperties properties = new LlmProperties(
                LlmProperties.OLLAMA, "http://localhost:11434", "primary-model", "fallback-model",
                "5m", maxRounds, deadline, 1, 16000, "", "");
        this.answerer = new OwnerAnswerer(
                model,
                new OllamaTurnOptions(properties),
                properties,
                new SystemPrompt(),
                engineTools,
                toolkitTools,
                clock);
    }

    public static AnswererHarness standard() {
        return new AnswererHarness(Duration.ofMinutes(5), 6, TurnFixtures.clock());
    }

    public static AnswererHarness withRoundCap(int maxRounds) {
        return new AnswererHarness(Duration.ofMinutes(5), maxRounds, TurnFixtures.clock());
    }

    /**
     * A harness whose turn outlives its deadline immediately: a one-millisecond
     * budget against a clock that jumps an hour per reading, so the check before
     * the model is even called trips.
     */
    public static AnswererHarness alreadyExpired() {
        return new AnswererHarness(Duration.ofMillis(1), 6, TurnFixtures.tickingClock());
    }

    /** Runs one turn with a scope open, the way the reply handler does. */
    public Outcome answer(String question) {
        TurnScope scope = new TurnScope(TurnFixtures.context(question), clock.instant(), deadline, maxRounds);
        try {
            scope.open();
            Answerer.Answer answer = answerer.answer(
                    new GateRequest(question, TurnFixtures.OWNER),
                    Answerer.AnswerContext.of(TurnFixtures.OWNER));
            return new Outcome(answer, scope);
        } finally {
            scope.close();
        }
    }

    /** What one turn produced, and the scope it produced it in. */
    public record Outcome(Answerer.Answer answer, TurnScope scope) {}
}
