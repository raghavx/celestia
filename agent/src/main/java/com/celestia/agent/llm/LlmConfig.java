package com.celestia.agent.llm;

import com.celestia.core.judgement.RulingPlanetsFactory;
import com.celestia.core.toolkit.KpToolkit;
import com.celestia.core.toolkit.PromiseEvaluator;
import com.celestia.ephemeris.HouseProvider;
import com.celestia.ephemeris.PositionProvider;
import com.celestia.ephemeris.SunriseProvider;
import com.celestia.guardrail.GuardedPipeline;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.util.concurrent.Semaphore;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The chat model, the toolkit beans its tools need, and the turn permit
 * (SPEC-012 T005).
 *
 * <p><b>One model bean, two model names.</b> The primary and the fallback differ
 * only in the {@code model} option, which is supplied per request, so a fallback
 * switch is a different option set rather than a second client to keep in step
 * (ADR-0009). {@link OwnerAnswerer} is the only type that calls it.
 *
 * <p><b>The permit is the handler's to take, not the answerer's.</b> This class
 * only provides it; {@code GuardedReplyHandler} acquires it after the entitlement
 * check and before the gate (FR-010b), so a sender who may not be answered cannot
 * consume the only permit and lock the owner out.
 *
 * <p>Keep-alive is the server's concern, not the client's: the VM stays warm
 * (ADR-0010), so nothing here retries a cold start.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    @Bean
    OllamaApi ollamaApi(LlmProperties properties) {
        return OllamaApi.builder().baseUrl(properties.baseUrl()).build();
    }

    /**
     * Wraps Spring AI's default manager so a turn can be bounded and recorded —
     * see {@link TurnToolCallingManager}.
     */
    @Bean
    ToolCallingManager turnToolCallingManager(Clock clock) {
        return new TurnToolCallingManager(DefaultToolCallingManager.builder().build(), clock);
    }

    @Bean
    OllamaChatModel ollamaChatModel(
            OllamaApi api, LlmProperties properties, ToolCallingManager toolCallingManager) {
        return OllamaChatModel.builder()
                .ollamaApi(api)
                .toolCallingManager(toolCallingManager)
                .observationRegistry(ObservationRegistry.NOOP)
                .options(OllamaChatOptions.builder()
                        .model(properties.model())
                        .keepAlive(properties.keepAlive())
                        .build())
                .build();
    }

    /**
     * The turn permit (FR-010a). Sized from {@code max-concurrent-turns} — one for
     * the preview. An unbounded queue in front of a single model is how a slow
     * reply becomes an outage, so a sender who cannot get the permit is told to
     * try again rather than queued.
     */
    @Bean
    Semaphore turnPermit(LlmProperties properties) {
        return new Semaphore(properties.maxConcurrentTurns());
    }

    /**
     * SPEC-011's guarded path, composed from its bundled rules. The only thing
     * that calls the answerer (Constitution V), and unchanged by this feature —
     * which was the point of building it first.
     */
    @Bean
    GuardedPipeline guardedPipeline() {
        return GuardedPipeline.bundled();
    }

    /** SPEC-010's toolkit, read from bundled data — the agent's knowledge base. */
    @Bean
    KpToolkit kpToolkit() {
        return KpToolkit.bundled();
    }

    /** SPEC-010's rule evaluator: deciding which rules a chart satisfies is code, never the model. */
    @Bean
    PromiseEvaluator promiseEvaluator(KpToolkit toolkit) {
        return new PromiseEvaluator(toolkit);
    }

    @Bean
    RulingPlanetsFactory rulingPlanetsFactory(
            PositionProvider positions, HouseProvider houses, SunriseProvider sunrise) {
        return new RulingPlanetsFactory(positions, houses, sunrise);
    }
}
