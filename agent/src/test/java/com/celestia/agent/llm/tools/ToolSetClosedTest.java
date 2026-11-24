package com.celestia.agent.llm.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.agent.llm.ToolNames;
import com.celestia.agent.llm.TurnFixtures;
import com.celestia.core.horary.HoraryChartFactory;
import com.celestia.core.judgement.RulingPlanetsFactory;
import com.celestia.core.toolkit.KpToolkit;
import com.celestia.core.toolkit.PromiseEvaluator;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHoraryHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisSunriseProvider;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

/**
 * SPEC-012 FR-007: the tool set is <b>closed</b>.
 *
 * <p>The registered callbacks must equal the expected list exactly, so an
 * accidentally-added {@code @Tool} fails the build rather than quietly widening
 * what the model can reach.
 */
class ToolSetClosedTest {

    private static List<String> registeredToolNames() {
        SwissEphemerisConfig config = SwissEphemerisConfig.resolve();
        var positions = new SwissEphemerisPositionProvider(config);
        var houses = new SwissEphemerisHouseProvider(config);
        EngineTools engine = new EngineTools(
                new RulingPlanetsFactory(positions, houses, new SwissEphemerisSunriseProvider(config)),
                new HoraryChartFactory(positions, new SwissEphemerisHoraryHouseProvider(config)),
                TurnFixtures.clock());
        KpToolkit toolkit = KpToolkit.bundled();
        ToolkitTools toolkitTools = new ToolkitTools(toolkit, new PromiseEvaluator(toolkit));

        ToolCallback[] callbacks = ToolCallbacks.from(engine, toolkitTools);
        return Arrays.stream(callbacks)
                .map(callback -> callback.getToolDefinition().name())
                .sorted()
                .toList();
    }

    @Test
    @DisplayName("the model can reach exactly these tools and nothing else")
    void theToolSetIsExactlyTheExpectedList() {
        assertThat(registeredToolNames()).isEqualTo(ToolNames.all());
    }

    @Test
    @DisplayName("the engine tools and the toolkit tools are both present and named apart")
    void theTwoFamiliesAreDistinct() {
        // Only a toolkit call can ground an interpretation (SC-002), so the split
        // has to be real and not just a comment.
        assertThat(ToolNames.ENGINE).doesNotContainAnyElementsOf(ToolNames.TOOLKIT);
        assertThat(registeredToolNames()).containsAll(ToolNames.ENGINE).containsAll(ToolNames.TOOLKIT);
        assertThat(ToolNames.TOOLKIT).hasSize(5); // SPEC-010's five
    }
}
