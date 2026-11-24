package com.celestia.agent.llm;

import java.time.Duration;
import java.util.List;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * The per-request options for one turn, as the configured provider builds them
 * (SPEC-012, ADR-0009's 2026-11-24 amendment).
 *
 * <p><b>Why this exists.</b> Everything else about the answering path is already
 * provider-agnostic: {@link OwnerAnswerer} holds a {@code ChatModel},
 * {@link TurnToolCallingManager} wraps the generic {@code ToolCallingManager}, and
 * both single-path ArchUnit rules are written over chat-model packages and call
 * sites. The one provider-specific thing left was the options object — Ollama
 * wants a keep-alive, Anthropic wants adaptive thinking and an effort level — so
 * that is the one thing behind a seam.
 *
 * <p>Model ids are configuration (ADR-0009), and so now is the provider. The
 * answerer still names the model per request, which is what makes the fallback
 * switch an option set rather than a second client.
 */
public interface TurnOptions {

    /**
     * Options for one call: the model to use and the closed tool set it may reach.
     *
     * @param modelName the primary or the fallback model — the answerer decides
     *     which, so this must not be read from the provider's defaults
     * @param callbacks the fixed tool set (FR-007)
     * @param remaining what is left of the turn's budget. A provider that
     *     supports a per-request timeout <b>must</b> use this rather than a fixed
     *     value, so that rounds cannot sum past the deadline (T045, FR-010)
     */
    ToolCallingChatOptions forModel(String modelName, List<ToolCallback> callbacks, Duration remaining);

    /** Which provider built these, for the turn record and the logs. */
    String providerName();
}
