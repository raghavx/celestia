package com.celestia.agent.llm;

import com.celestia.agent.AgentMessages;
import com.celestia.agent.llm.tools.EngineTools;
import com.celestia.agent.llm.tools.ToolkitTools;
import com.celestia.core.dasha.DashaPeriod;
import com.celestia.core.toolkit.Sensitivity;
import com.celestia.guardrail.Answerer;
import com.celestia.guardrail.GateRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * The answering seam the guardrail calls, and <b>the only class that invokes a
 * chat model</b> (SPEC-012 FR-004, Constitution V).
 *
 * <p>{@code GuardedPipeline} is its only caller, and calls it only on an
 * {@code ALLOWED} decision. Two ArchUnit rules hold that structurally: one names
 * the two classes that may invoke a chat model, the other keeps Spring AI types
 * inside {@code agent.llm}.
 *
 * <p><b>What it does.</b> Builds the turn's request from the pre-fetched chart and
 * the hardened prompt, runs the tool loop, and returns the draft with the most
 * severe sensitivity the answer actually drew on — <b>computed by the
 * application</b>, never read out of the draft and never decided by the model
 * (SPEC-011 FR-014).
 *
 * <p><b>What it never does.</b> Throw. A fault is a fixed message to the owner and
 * a recorded fault on the turn, not an exception out of the reply path (FR-010).
 * Nor does it write an advisory sentence or a crisis line — both belong to the
 * output stage (FR-016).
 *
 * <p><b>Why {@code ChatModel} and not {@code ChatClient}.</b> The fluent client
 * runs the tool loop privately, and Spring AI 2.0 no longer exposes a switch to
 * take it over. The round cap, the deadline and the per-call records all live in
 * {@link TurnToolCallingManager}, which the model calls once per round — so the
 * model interface is the one that can be bounded, and the client is the one that
 * cannot.
 */
@Component
public class OwnerAnswerer implements Answerer {

    private static final Logger log = LoggerFactory.getLogger(OwnerAnswerer.class);

    private final ChatModel model;
    private final TurnOptions turnOptions;
    private final LlmProperties properties;
    private final SystemPrompt systemPrompt;
    private final EngineTools engineTools;
    private final ToolkitTools toolkitTools;
    private final Clock clock;

    public OwnerAnswerer(
            ChatModel model,
            TurnOptions turnOptions,
            LlmProperties properties,
            SystemPrompt systemPrompt,
            EngineTools engineTools,
            ToolkitTools toolkitTools,
            Clock clock) {
        this.model = model;
        this.turnOptions = turnOptions;
        this.properties = properties;
        this.systemPrompt = systemPrompt;
        this.engineTools = engineTools;
        this.toolkitTools = toolkitTools;
        this.clock = clock;
    }

    @Override
    public Answer answer(GateRequest request, AnswerContext context) {
        TurnScope scope = TurnScope.current();
        List<Message> messages = List.of(
                new SystemMessage(systemPrompt.text()),
                new UserMessage(userMessage(scope, request.text())));

        try {
            return attempt(scope, messages, properties.model(), false);
        } catch (TurnBoundExceeded bound) {
            // A round cap is a prompt defect and a timeout is a hosting one. Both
            // end the turn, and the record keeps them apart.
            log.warn("turn ended on its {} bound: {}", bound.kind(), bound.getMessage());
            scope.faulted(bound.kind());
            return fixed();
        } catch (RuntimeException primaryFailed) {
            TurnDetail.FaultKind kind = classify(primaryFailed);
            log.warn("primary model {} failed ({}); switching to the fallback {}",
                    properties.model(), kind, properties.fallbackModel(), primaryFailed);
            return fallback(scope, messages);
        }
    }

    /**
     * One attempt against one model. A malformed tool-call response or repeated
     * failure switches the turn to the fallback (ADR-0009) — on a small model that
     * is the <b>normal</b> path, which is why it is recorded as a switch rather
     * than as a fault.
     */
    private Answer attempt(TurnScope scope, List<Message> messages, String modelName, boolean isFallback) {
        scope.usedModel(modelName, isFallback);
        scope.checkDeadline(clock.instant());
        ChatResponse response = model.call(new Prompt(messages, options(modelName)));
        String draft = draftOf(response);
        if (draft.isBlank()) {
            throw new IllegalStateException(modelName + " returned an empty draft");
        }
        return new Answer(draft, scope.sensitivityUsed());
    }

    private Answer fallback(TurnScope scope, List<Message> messages) {
        try {
            return attempt(scope, messages, properties.fallbackModel(), true);
        } catch (TurnBoundExceeded bound) {
            scope.faulted(bound.kind());
            return fixed();
        } catch (RuntimeException fallbackFailed) {
            // Both models failed the same way, which is the point at which nobody
            // is going to answer this question.
            TurnDetail.FaultKind kind = classify(fallbackFailed);
            log.error("fallback model {} also failed ({})", properties.fallbackModel(), kind, fallbackFailed);
            scope.faulted(kind);
            return fixed();
        }
    }

    /**
     * The fixed message for a turn that could not be completed.
     *
     * <p>{@link Sensitivity#NONE} deliberately: an advisory sentence appended to
     * "I couldn't complete your reading" would be noise, and the sensitivity the
     * turn had seen so far describes content the owner never received.
     */
    private static Answer fixed() {
        return new Answer(AgentMessages.COULD_NOT_COMPLETE, Sensitivity.NONE);
    }

    /**
     * The fixed tool set and the model for this call, built by whichever provider
     * is configured (ADR-0009's amendment). The answerer names the model per
     * request, which is what makes a fallback switch an option set rather than a
     * second client.
     */
    private ToolCallingChatOptions options(String modelName) {
        ToolCallback[] callbacks = ToolCallbacks.from(engineTools, toolkitTools);
        return turnOptions.forModel(modelName, List.of(callbacks));
    }

    private static String draftOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("the model returned no generation");
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text.trim();
    }

    /**
     * Which fault a model failure is. Tool-argument parsing is the failure a small
     * model makes constantly (ADR-0009), and it is worth telling apart from a host
     * that cannot be reached at all.
     */
    private static TurnDetail.FaultKind classify(RuntimeException failure) {
        StringBuilder trail = new StringBuilder();
        for (Throwable t = failure; t != null && trail.length() < 2000; t = t.getCause()) {
            trail.append(t.getClass().getSimpleName()).append(' ').append(t.getMessage()).append(' ');
        }
        String text = trail.toString().toLowerCase(Locale.ROOT);
        boolean looksLikeToolJson = text.contains("json")
                || text.contains("tool call")
                || text.contains("toolcall")
                || text.contains("arguments")
                || text.contains("deserial");
        return looksLikeToolJson
                ? TurnDetail.FaultKind.MALFORMED_TOOL_JSON
                : TurnDetail.FaultKind.MODEL_UNREACHABLE;
    }

    /**
     * The question, with the chart and the running dasha already in hand (FR-009).
     *
     * <p>Supplied as context rather than fetched by the model: it removes a whole
     * class of failure — the model asking for, guessing at or mis-ordering the
     * basics — and it is what keeps the first answer fast enough to feel like a
     * conversation. The tools remain for everything beyond the basics.
     *
     * <p>Nothing here is carried from a previous turn, because nothing of a
     * previous turn exists (FR-018).
     */
    private String userMessage(TurnScope scope, String question) {
        TurnContext turn = scope.context();
        StringBuilder out = new StringBuilder(1024);
        out.append("The querent asks:\n").append(question).append("\n\n");
        out.append("Their chart, already cast (you do not need to compute any of this):\n");
        for (int house = 1; house <= 12; house++) {
            var chain = turn.chartToJudge().cusp(house).lordChain();
            out.append("- cusp ").append(house)
                    .append(": ").append(chain.sign().name())
                    .append(", star ").append(chain.nakshatra().name())
                    .append(" (lord ").append(chain.starLord().name()).append(")")
                    .append(", sub lord ").append(chain.subLord().name())
                    .append('\n');
        }
        out.append("\nPeriods running now:\n");
        for (DashaPeriod period : turn.chart().dasha().running(clock.instant(), 3).stack()) {
            out.append("- ").append(period.level().name())
                    .append(' ').append(period.lord().name())
                    .append(" (").append(LocalDate.ofInstant(period.start(), ZoneOffset.UTC))
                    .append(" to ").append(LocalDate.ofInstant(period.end(), ZoneOffset.UTC))
                    .append(")\n");
        }
        if (turn.suppliedChart().isPresent()) {
            out.append("\nThe querent also supplied another person's birth details this turn; "
                    + "the chart above is that person's.\n");
        }
        return out.toString();
    }
}
