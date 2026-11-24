package com.celestia.agent.llm;

import com.anthropic.models.messages.OutputConfig;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Turn options for the Claude API (ADR-0009's 2026-11-24 amendment).
 *
 * <p><b>Adaptive thinking, never a token budget.</b> Sonnet 5 rejects the fixed
 * thinking-budget parameter outright — {@code budget_tokens} is a 400 — so depth
 * is controlled by {@code celestia.llm.effort} instead, which is the current API's
 * own knob. {@code thinkingAdaptive()} lets the model decide when to think, which
 * is what a turn of tool-driven KP judgement wants: some questions resolve in one
 * look-up and some need several.
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
    public ToolCallingChatOptions forModel(String modelName, List<ToolCallback> callbacks) {
        AnthropicChatOptions.Builder builder = AnthropicChatOptions.builder()
                .model(modelName)
                .maxTokens(properties.maxTokens())
                .toolCallbacks(callbacks)
                .thinkingAdaptive();
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
