package com.celestia.agent.llm;

import java.time.Duration;
import java.util.List;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Turn options for a self-hosted Ollama (ADR-0009, ADR-0010).
 *
 * <p>Keep-alive is passed through as the server writes it: how long Ollama holds
 * the weights resident is the server's concern, not the client's — long on a warm
 * VM, short on a laptop.
 */
public final class OllamaTurnOptions implements TurnOptions {

    private final LlmProperties properties;

    public OllamaTurnOptions(LlmProperties properties) {
        this.properties = properties;
    }

    /**
     * {@code remaining} is unused: Ollama's options carry no per-request timeout,
     * so this provider is bounded by the HTTP client's read timeout instead —
     * {@code spring.http.clients.read-timeout}, set from configuration. The
     * difference is real and worth knowing: a turn here is bounded by
     * {@code deadline + one read timeout} rather than by the deadline exactly,
     * because the deadline can only be checked between calls (T045, FR-010).
     */
    @Override
    public ToolCallingChatOptions forModel(
            String modelName, List<ToolCallback> callbacks, Duration remaining) {
        return OllamaChatOptions.builder()
                .model(modelName)
                .keepAlive(properties.keepAlive())
                .toolCallbacks(callbacks)
                .build();
    }

    @Override
    public String providerName() {
        return "ollama";
    }
}
