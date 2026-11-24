package com.celestia.agent.llm;

import com.celestia.core.judgement.RulingPlanetsFactory;
import com.celestia.core.toolkit.KpToolkit;
import com.celestia.core.toolkit.PromiseEvaluator;
import com.celestia.ephemeris.HouseProvider;
import com.celestia.ephemeris.PositionProvider;
import com.celestia.ephemeris.SunriseProvider;
import com.celestia.agent.entitlement.EntitlementProperties;
import com.celestia.agent.entitlement.OwnerOnlyAllowListGuard;
import com.celestia.guardrail.GuardedPipeline;
import java.time.Clock;
import java.util.concurrent.Semaphore;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * <p><b>And one provider of two</b>, chosen by {@code celestia.llm.provider}:
 * self-hosted Ollama (the committed default, so a clone with no credentials still
 * builds) or the Claude API (ADR-0009's 2026-11-24 amendment, the development
 * story). Only the per-request options differ between them — everything else in
 * the answering path was already provider-agnostic.
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


    /**
     * Wraps Spring AI's default manager so a turn can be bounded and recorded —
     * see {@link TurnToolCallingManager}.
     */
    @Bean
    ToolCallingManager turnToolCallingManager(Clock clock) {
        return new TurnToolCallingManager(DefaultToolCallingManager.builder().build(), clock);
    }

    /**
     * The per-request options for the configured provider, chosen by
     * {@code celestia.llm.provider}.
     *
     * <p><b>Neither branch defines a {@code ChatModel}.</b> Spring AI's own
     * autoconfiguration builds it — for Ollama and for Anthropic alike — and
     * <b>both factory methods take a {@link ToolCallingManager}</b>, so they pick
     * up {@link TurnToolCallingManager} and the turn stays bounded and recorded
     * whichever provider is serving. Defining one here collided with the
     * autoconfiguration's bean name, which is the framework saying the same thing.
     *
     * <p>So the only provider-specific thing left is the options object: Ollama
     * wants a keep-alive, Anthropic wants adaptive thinking and an effort level.
     * That is the whole of the seam.
     *
     * <p><b>Nested {@code @Configuration} classes of a {@code @Configuration} are
     * registered</b> — unlike nested ones inside a {@code @Component}, which are
     * silently ignored and cost this project a dead seed command once.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "celestia.llm.provider", havingValue = LlmProperties.OLLAMA,
            matchIfMissing = true)
    static class OllamaProvider {

        @Bean
        TurnOptions ollamaTurnOptions(LlmProperties properties) {
            return new OllamaTurnOptions(properties);
        }
    }

    /**
     * The Claude API (ADR-0009's 2026-11-24 amendment) — the development story,
     * because a small local model cannot drive eleven tool definitions at all.
     *
     * <p>Needs an <b>API key</b>, not a subscription: {@code ANTHROPIC_API_KEY}, or
     * {@code celestia.llm.api-key} when it must come from configuration. Nothing
     * here changes what is stored or what "forget me" deletes, and serving anyone
     * but the owner from a third-party API remains prohibited by the Technology
     * Constraints.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "celestia.llm.provider", havingValue = LlmProperties.ANTHROPIC)
    static class AnthropicProvider {

        @Bean
        TurnOptions anthropicTurnOptions(LlmProperties properties) {
            return new AnthropicTurnOptions(properties);
        }

        /**
         * Constitution v3.0.0's bounded exception, enforced at startup (ADR-0026
         * condition 1). Declared here because the condition applies <b>only</b>
         * while a third-party provider is selected — a self-hosted deployment may
         * allow-list whoever it likes.
         */
        @Bean
        OwnerOnlyAllowListGuard ownerOnlyAllowListGuard(
                LlmProperties properties, EntitlementProperties entitlement) {
            return new OwnerOnlyAllowListGuard(properties.provider(), entitlement);
        }
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
