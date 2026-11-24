package com.celestia.agent.llm;

import com.anthropic.models.messages.OutputConfig;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Turn options for the Claude API (ADR-0009's 2026-11-24 amendment).
 *
 * <p><b>Thinking is disabled, and it is a workaround rather than a preference.</b>
 * Spring AI 2.0.1's Anthropic integration <b>does not surface the response content
 * when thinking is enabled</b>: the reply lands in a package-private
 * {@code AnthropicAssistantMessage} wrapper, and the standard accessors —
 * {@code getText()} and {@code getToolCalls()} — both return empty. Measured on
 * 2026-11-24 (T044): with adaptive thinking, two tool rounds ran correctly and the
 * final response came back {@code end_turn} with <b>1,877 output tokens and zero
 * readable characters</b>; with thinking disabled, the same question returned
 * 2,183 characters of KP answer and the tool calls became visible. The tool loop
 * works either way, because {@code executeToolCalls} reads the raw response — it
 * is only the accessors that are blind.
 *
 * <p>There is no public accessor to work around it with, so the options are
 * thinking-off or no reply. If a later Spring AI release fixes the mapping, this
 * is a one-line change back to {@code thinkingAdaptive()} — and worth making, for
 * a turn of tool-driven KP judgement where some questions resolve in one look-up
 * and some need several.
 *
 * <p><b>Never a token budget</b> either way: Sonnet 5 rejects {@code budget_tokens}
 * outright (HTTP 400). {@code celestia.llm.effort} is the current API's knob for
 * depth, and it is left unset by default — it tunes thinking, so it has nothing to
 * do while thinking is off.
 *
 * <p><b>The timeout is the turn's remaining budget.</b> This provider supports a
 * per-request timeout, which is what lets the deadline be a bound rather than a
 * checkpoint: each call is given only the time left, so a turn's calls cannot sum
 * past it. Retries are off for the same reason — ADR-0009 makes the fallback model
 * the answer to a failing primary, and Spring AI's own default of ten attempts
 * with a five-times backoff is what turned a 120-second budget into 24 minutes
 * (T045).
 *
 * <p><b>{@code maxTokens} is a ceiling, not a reservation.</b> It is set well above
 * what a reply needs — FR-017 bounds the delivered reply at 4,096 characters, but
 * the model's output also carries tool-call blocks, and hitting the cap truncates
 * mid-thought and wastes the turn. Unused headroom costs nothing.
 */
public final class AnthropicTurnOptions implements TurnOptions {

    private final LlmProperties properties;

    public AnthropicTurnOptions(LlmProperties properties) {
        this.properties = properties;
    }

    @Override
    public ToolCallingChatOptions forModel(
            String modelName, List<ToolCallback> callbacks, Duration remaining) {
        AnthropicChatOptions.Builder builder = AnthropicChatOptions.builder()
                .model(modelName)
                .maxTokens(properties.maxTokens())
                .toolCallbacks(callbacks)
                // The turn's remaining budget, not a fixed value: however many
                // rounds this turn takes, their timeouts cannot sum past the
                // deadline (T045, FR-010).
                .timeout(remaining)
                // No SDK-level retries. ADR-0009 makes the FALLBACK MODEL the
                // response to a failing primary; a retry underneath that competes
                // with it, multiplies the latency and makes the budget a fiction.
                .maxRetries(0)
                // Thinking OFF, and not by preference — see the class note. Spring
                // AI 2.0.1 does not surface the response content when it is on.
                .thinkingDisabled();
        effort().ifPresent(builder::effort);
        return builder.build();
    }

    /**
     * {@code celestia.llm.effort}, when set. Left unset the API's own default
     * applies; {@code low} is worth trying once the prompt is settled, because a
     * grounded KP answer is mostly retrieval and the judgement is already code's.
     */
    private java.util.Optional<OutputConfig.Effort> effort() {
        String configured = properties.effort();
        if (configured == null || configured.isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(
                OutputConfig.Effort.of(configured.trim().toLowerCase(Locale.ROOT)));
    }

    @Override
    public String providerName() {
        return "anthropic";
    }
}
