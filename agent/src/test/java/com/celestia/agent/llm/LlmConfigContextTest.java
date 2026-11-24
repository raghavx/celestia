package com.celestia.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.horary.HoraryChartFactory;
import com.celestia.core.judgement.RulingPlanetsFactory;
import com.celestia.ephemeris.HouseProvider;
import com.celestia.ephemeris.PositionProvider;
import com.celestia.ephemeris.SunriseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHoraryHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisSunriseProvider;
import java.time.Clock;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.celestia.agent.entitlement.EntitlementProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The model wiring composes, and composes <b>once</b>.
 *
 * <p>The Ollama starter brings its own autoconfiguration for
 * {@code OllamaApi} and {@code OllamaChatModel}, and this feature declares both
 * explicitly in {@link LlmConfig} — because the chat model must be built around
 * {@link TurnToolCallingManager}, without which a turn has no round cap and no
 * tool-call record. A second {@code ChatModel} bean would make the one the
 * answerer gets a matter of bean ordering, and the bounded one could quietly lose.
 *
 * <p>No Docker and no Ollama: nothing here connects to anything. It asserts the
 * shape of the context, which is the part that would otherwise only be discovered
 * on a machine that can start the whole application.
 */
class LlmConfigContextTest {

    @Configuration(proxyBeanMethods = false)
    static class EngineStubs {
        @Bean
        Clock clock() {
            return TurnFixtures.clock();
        }

        @Bean
        PositionProvider positions(SwissEphemerisConfig config) {
            return new SwissEphemerisPositionProvider(config);
        }

        @Bean
        HouseProvider houses(SwissEphemerisConfig config) {
            return new SwissEphemerisHouseProvider(config);
        }

        @Bean
        SunriseProvider sunrise(SwissEphemerisConfig config) {
            return new SwissEphemerisSunriseProvider(config);
        }

        @Bean
        SwissEphemerisConfig swissEphemerisConfig() {
            return SwissEphemerisConfig.resolve();
        }

        @Bean
        HoraryChartFactory horaryCharts(SwissEphemerisConfig config, PositionProvider positions) {
            return new HoraryChartFactory(positions, new SwissEphemerisHoraryHouseProvider(config));
        }
    }

    /** The entitlement allow-list, bindable — the guard of ADR-0026 condition 1 reads it. */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EntitlementProperties.class)
    static class EntitlementProps {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    OllamaApiAutoConfiguration.class, OllamaChatAutoConfiguration.class,
                    AnthropicChatAutoConfiguration.class))
            .withUserConfiguration(EngineStubs.class, EntitlementProps.class, LlmConfig.class)
            .withPropertyValues(
                    // Spring AI's own switch, which application.yml drives from the
                    // same environment variable — without it BOTH autoconfigurations
                    // activate, which is exactly what the property exists to prevent.
                    "spring.ai.model.chat=ollama",
                    "celestia.llm.provider=ollama",
                    "celestia.llm.max-tokens=16000",
                    "celestia.llm.base-url=http://localhost:11434",
                    "celestia.llm.model=primary-model",
                    "celestia.llm.fallback-model=fallback-model",
                    "celestia.llm.keep-alive=5m",
                    "celestia.llm.max-tool-rounds=6",
                    "celestia.llm.turn-deadline=PT60S",
                    "celestia.llm.max-concurrent-turns=1");

    @Test
    @DisplayName("exactly one ChatModel, and it is built around the bounded manager")
    void thereIsExactlyOneChatModel() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            // Spring AI's autoconfiguration builds it, and its factory takes a
            // ToolCallingManager — so the model the answerer gets is the bounded
            // one. A second would make that a matter of bean ordering.
            assertThat(context.getBeansOfType(ChatModel.class)).hasSize(1);
            assertThat(context.getBeansOfType(OllamaApi.class)).hasSize(1);
            assertThat(context.getBean(ToolCallingManager.class))
                    .isInstanceOf(TurnToolCallingManager.class);
            assertThat(context.getBean(TurnOptions.class).providerName()).isEqualTo("ollama");
        });
    }

    @Test
    @DisplayName("the turn permit is sized from configuration — one for the preview")
    void thePermitIsSizedFromConfiguration() {
        runner.run(context -> assertThat(context.getBean(Semaphore.class).availablePermits()).isEqualTo(1));
        runner.withPropertyValues("celestia.llm.max-concurrent-turns=3")
                .run(context -> assertThat(context.getBean(Semaphore.class).availablePermits()).isEqualTo(3));
    }

    @Test
    @DisplayName("ollama is the default, so a clone with no credentials still starts")
    void ollamaIsTheCommittedDefault() {
        // ADR-0009's amendment makes the Claude API the development story, but the
        // committed default must need no credentials or `./mvnw verify` would
        // require an API key on every machine.
        new ApplicationContextRunner()
                .withUserConfiguration(EngineStubs.class, EntitlementProps.class, LlmConfig.class)
                .withPropertyValues(
                        "spring.ai.model.chat=ollama",
                        "celestia.llm.base-url=http://localhost:11434",
                        "celestia.llm.model=primary-model",
                        "celestia.llm.fallback-model=fallback-model",
                        "celestia.llm.keep-alive=5m",
                        "celestia.llm.max-tool-rounds=6",
                        "celestia.llm.turn-deadline=PT60S",
                        "celestia.llm.max-concurrent-turns=1")
                .run(context -> assertThat(context.getBean(TurnOptions.class).providerName())
                        .isEqualTo("ollama"));
    }

    @Test
    @DisplayName("the anthropic provider contributes its options and no second chat model")
    void theAnthropicProviderSwapsOnlyTheOptions() {
        // The ChatModel comes from Spring AI's own autoconfiguration, whose factory
        // takes a ToolCallingManager — so the turn stays bounded by
        // TurnToolCallingManager whichever provider is selected. All this config
        // contributes is the per-request options.
        runner.withPropertyValues(
                        "celestia.llm.provider=anthropic",
                        "spring.ai.model.chat=anthropic",
                        "spring.ai.anthropic.api-key=test-key-not-used",
                        "celestia.entitlement.allow-list=+919876543210")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(TurnOptions.class).providerName()).isEqualTo("anthropic");
                    // Both starters are on the classpath on purpose, so an idle
                    // OllamaApi bean may exist — it is a REST client object that
                    // opens nothing. What must be unambiguous is the chat model.
                    assertThat(context.getBeansOfType(ChatModel.class))
                            .as("exactly one, from Spring AI's Anthropic autoconfiguration")
                            .hasSize(1);
                    assertThat(context.getBean(ChatModel.class).getClass().getSimpleName())
                            .isEqualTo("AnthropicChatModel");
                    assertThat(context.getBean(ToolCallingManager.class))
                            .isInstanceOf(TurnToolCallingManager.class);
                });
    }

    @Test
    @DisplayName("base-url is not required for the Claude API, and an unknown provider fails fast")
    void providerAwareValidation() {
        runner.withPropertyValues(
                        "celestia.llm.provider=anthropic",
                        "spring.ai.model.chat=anthropic",
                        "spring.ai.anthropic.api-key=test-key-not-used",
                        "celestia.entitlement.allow-list=+919876543210",
                        "celestia.llm.base-url=")
                .run(context -> assertThat(context).hasNotFailed());
        runner.withPropertyValues("celestia.llm.provider=vertex")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("on a third-party API the app refuses to start unless the allow-list holds one")
    void theBoundedExceptionIsEnforcedNotTrusted() {
        // Constitution v3.0.0 / ADR-0026 condition 1. A working preview on a
        // third-party API creates pressure to add a second number, and that is the
        // transition the deployment rule exists to stop — so it is a startup
        // invariant, not a sentence in a document.
        var anthropic = runner.withPropertyValues(
                "celestia.llm.provider=anthropic",
                "spring.ai.model.chat=anthropic",
                "spring.ai.anthropic.api-key=test-key-not-used");

        anthropic.withPropertyValues("celestia.entitlement.allow-list=+919876543210")
                .run(context -> assertThat(context).hasNotFailed());

        anthropic.withPropertyValues("celestia.entitlement.allow-list=+919876543210,+447700900123")
                .run(context -> assertThat(context)
                        .as("a second number must stop the application, not widen the exception")
                        .hasFailed());

        anthropic.withPropertyValues("celestia.entitlement.allow-list=")
                .run(context -> assertThat(context)
                        .as("and an empty list is not a quiet pass either")
                        .hasFailed());
    }

    @Test
    @DisplayName("a self-hosted deployment may allow-list whoever it likes")
    void theExceptionDoesNotConstrainSelfHosting() {
        // The condition exists because of where the data goes. Nothing leaves our
        // infrastructure on Ollama, so there is nothing for it to bound.
        runner.withPropertyValues("celestia.entitlement.allow-list=+919876543210,+447700900123")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("a non-positive bound fails at startup rather than meaning \"unbounded\"")
    void aMisconfiguredBoundFailsFast() {
        runner.withPropertyValues("celestia.llm.max-tool-rounds=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("celestia.llm.model=")
                .run(context -> assertThat(context).hasFailed());
    }
}
