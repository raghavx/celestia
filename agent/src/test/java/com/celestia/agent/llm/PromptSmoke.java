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
import com.celestia.guardrail.FixedMessages;
import com.celestia.guardrail.GateRequest;
import com.celestia.guardrail.GuardedPipeline;
import com.celestia.guardrail.OutputResult;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * SPEC-012 <b>T044</b> — exercise the prompt and the tool adapters against the
 * deployed provider, <em>before</em> T041 asks a practitioner to judge what they
 * produce.
 *
 * <p><b>No personal data.</b> It runs on the fixture chart, so it answers the
 * question T044 actually asks — does this prompt hold on a fluent model? — without
 * sending anyone's real birth details. T041 is the one that needs the owner's own
 * chart, and it is a different task for that reason.
 *
 * <p><b>Not in any build.</b> No {@code Test}/{@code IT} suffix, so neither
 * surefire nor failsafe collects it. Run deliberately:
 *
 * <pre>{@code
 * set -a; . ~/.celestia-eval.env; set +a
 * ./mvnw -pl agent -am test -Dtest=PromptSmoke -Dsurefire.failIfNoSpecifiedTests=false
 * }</pre>
 *
 * <p>It asserts nothing and reports everything. The four things worth reading, in
 * order of how likely they were to be wrong: was anything <b>withheld</b> by the
 * leak scan (a fluent model is more able to paraphrase its instructions, not
 * less); did every judgement rest on a <b>toolkit</b> call; were the no-basis
 * questions <b>declined</b>; and did any reply exceed the <b>4,096-character</b>
 * budget.
 */
@Tag("eval")
class PromptSmoke {

    /** A representative slice: two answerable, one conditional, one with no basis. */
    private static final List<String> QUESTIONS = List.of(
            "when will I get married?",
            "what kind of career suits my chart?",
            "is there anything in my chart I should watch about my health?",
            "which lottery numbers should I pick this week?");

    @Test
    void exerciseThePromptAgainstTheDeployedProvider() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("SKIPPED: ANTHROPIC_API_KEY is not set");
            return;
        }
        String model = Optional.ofNullable(System.getenv("CELESTIA_LLM_MODEL_ANTHROPIC"))
                .filter(m -> !m.isBlank())
                .orElse("claude-sonnet-5");

        LlmProperties properties = new LlmProperties(
                LlmProperties.ANTHROPIC, "", model, model, "5m", 6, Duration.ofSeconds(120), 1,
                16000, "", apiKey);

        Clock clock = Clock.systemUTC();
        SwissEphemerisConfig config = SwissEphemerisConfig.resolve();
        var positions = new SwissEphemerisPositionProvider(config);
        var houses = new SwissEphemerisHouseProvider(config);
        EngineTools engineTools = new EngineTools(
                new RulingPlanetsFactory(positions, houses, new SwissEphemerisSunriseProvider(config)),
                new HoraryChartFactory(positions, new SwissEphemerisHoraryHouseProvider(config)),
                clock,
                properties);
        KpToolkit toolkit = KpToolkit.bundled();
        ToolkitTools toolkitTools = new ToolkitTools(toolkit, new PromiseEvaluator(toolkit));

        // The chat model comes from Spring AI's own autoconfiguration, as it does in
        // production: the SDK's HTTP client is not on this module's compile
        // classpath, and the autoconfiguration's factory takes a
        // ToolCallingManager — so the model it builds is wrapped in the bounded
        // one, exactly as LlmConfig relies on.
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AnthropicChatAutoConfiguration.class))
                .withBean(ToolCallingManager.class, () -> new TurnToolCallingManager(
                        DefaultToolCallingManager.builder().build(), clock))
                .withPropertyValues(
                        "spring.ai.anthropic.api-key=" + apiKey,
                        "spring.ai.anthropic.chat.options.model=" + model)
                .run(context -> runQuestions(
                        context.getBean(ChatModel.class), context.getBean(ToolCallingManager.class),
                        properties, engineTools, toolkitTools, clock, model));
    }

    private void runQuestions(
            ChatModel chatModel,
            ToolCallingManager manager,
            LlmProperties properties,
            EngineTools engineTools,
            ToolkitTools toolkitTools,
            Clock clock,
            String model) {
        SystemPrompt prompt = new SystemPrompt();
        OwnerAnswerer answerer = new OwnerAnswerer(
                chatModel, manager, new AnthropicTurnOptions(properties), properties, prompt,
                engineTools, toolkitTools, clock);
        GuardedPipeline pipeline = GuardedPipeline.bundled();

        System.out.println("=== T044: " + model + ", fixture chart (Pune 1985), no personal data ===");
        for (String question : QUESTIONS) {
            TurnScope scope = new TurnScope(
                    TurnFixtures.context(question), clock.instant(), Duration.ofSeconds(120), 6);
            long startedAt = System.currentTimeMillis();
            OutputResult result;
            try {
                scope.open();
                result = pipeline.handle(
                        new GateRequest(question, TurnFixtures.OWNER),
                        new Answerer.AnswerContext(TurnFixtures.OWNER,
                                List.of(prompt.text())),
                        answerer);
            } finally {
                scope.close();
            }
            long millis = System.currentTimeMillis() - startedAt;

            System.out.println();
            System.out.println("--- Q: " + question);
            System.out.printf("    outcome        : %s%n",
                    !result.delivered() ? "WITHHELD (" + result.withheldReason().orElse(null) + ")"
                            : result.text().contains(FixedMessages.refusalRedirect())
                                    ? "REDIRECT (out-of-scope marker)" : "delivered");
            System.out.printf("    toolkit calls  : %d of %d tool calls%n",
                    scope.toolkitCallCount(), scope.toolCalls().size());
            System.out.printf("    tools used     : %s%n",
                    scope.toolCalls().stream().map(ToolCallRecord::tool).distinct().toList());
            System.out.printf("    references     : %d sources behind s1…%n", scope.references().size());
            System.out.printf("    rounds         : %d   sensitivity: %s   model: %s%n",
                    scope.rounds(), scope.sensitivityUsed(), scope.modelUsed().orElse("-"));
            System.out.printf("    length         : %d chars (budget 4096)   elapsed: %.1fs%n",
                    result.text().length(), millis / 1000.0);
            System.out.println("    reply:");
            result.text().lines().forEach(l -> System.out.println("      " + l));
        }
        System.out.println();
        System.out.println("=== read the four things: withheld? grounded? declined? over budget? ===");
    }
}
