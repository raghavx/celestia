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
import org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    OllamaApiAutoConfiguration.class, OllamaChatAutoConfiguration.class))
            .withUserConfiguration(EngineStubs.class, LlmConfig.class)
            .withPropertyValues(
                    "celestia.llm.base-url=http://localhost:11434",
                    "celestia.llm.model=primary-model",
                    "celestia.llm.fallback-model=fallback-model",
                    "celestia.llm.keep-alive=5m",
                    "celestia.llm.max-tool-rounds=6",
                    "celestia.llm.turn-deadline=PT60S",
                    "celestia.llm.max-concurrent-turns=1");

    @Test
    @DisplayName("exactly one ChatModel, and it is the one built around the bounded manager")
    void thereIsExactlyOneChatModel() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(ChatModel.class))
                    .as("a second ChatModel would make the bounded one a matter of bean ordering")
                    .hasSize(1);
            assertThat(context.getBeansOfType(OllamaApi.class)).hasSize(1);
            assertThat(context.getBean(ToolCallingManager.class))
                    .isInstanceOf(TurnToolCallingManager.class);
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
    @DisplayName("a non-positive bound fails at startup rather than meaning \"unbounded\"")
    void aMisconfiguredBoundFailsFast() {
        runner.withPropertyValues("celestia.llm.max-tool-rounds=0")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("celestia.llm.model=")
                .run(context -> assertThat(context).hasFailed());
    }
}
