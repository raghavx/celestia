package com.celestia.agent.llm;

import java.time.Duration;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The chat model's configuration (SPEC-012 T002). Where the model runs and which
 * model it is are configuration, not code (ADR-0009, ADR-0010), so moving from a
 * laptop's Ollama to the warm GPU VM is a property change.
 *
 * <p><b>Validated at bind time.</b> A misconfiguration must not silently mean
 * "unbounded": a non-positive round cap or deadline would turn FR-010's bound
 * into no bound at all, and a blank model name would fail on the owner's first
 * question rather than at startup (data-model.md "Validation at load").
 *
 * @param provider which model provider serves a turn — {@code ollama} (the
 *     committed default, so a clone with no credentials still builds and tests)
 *     or {@code anthropic} (ADR-0009's 2026-11-24 amendment: the development
 *     story, because a local small model cannot drive this tool surface at all)
 * @param baseUrl the Ollama server — a local {@code ollama serve} in development,
 *     the warm VM in production. Required for {@code ollama}; ignored otherwise
 * @param model the primary model
 * @param fallbackModel the tool-calling fallback; not optional, because this
 *     feature is almost entirely tool calling (ADR-0009)
 * @param keepAlive how long Ollama holds the model resident; the server's
 *     concern, passed through as it writes it ({@code 30m}, {@code -1} for ever)
 * @param maxToolRounds the round cap — tool-call rounds per turn (FR-010)
 * @param turnDeadline wall clock for the whole turn (FR-010)
 * @param maxConcurrentTurns how many turns may run at once — <b>one</b> for the
 *     preview (FR-010a)
 * @param maxTokens the output ceiling per call. A ceiling, not a reservation:
 *     unused headroom costs nothing, and hitting the cap truncates mid-thought
 *     and wastes the turn. Anthropic only
 * @param effort thinking depth — {@code low} … {@code max}, or blank for the
 *     provider's default. <b>Not</b> a token budget: Sonnet 5 rejects
 *     {@code budget_tokens} outright. Anthropic only
 * @param apiKey the Claude API key, or blank to read {@code ANTHROPIC_API_KEY}
 *     from the environment. <b>An API key, not a subscription.</b> Anthropic only
 */
@ConfigurationProperties("celestia.llm")
public record LlmProperties(
        String provider,
        String baseUrl,
        String model,
        String fallbackModel,
        String keepAlive,
        int maxToolRounds,
        Duration turnDeadline,
        int maxConcurrentTurns,
        int maxTokens,
        String effort,
        String apiKey) {

    /** The provider ids {@code celestia.llm.provider} accepts. */
    public static final String OLLAMA = "ollama";

    public static final String ANTHROPIC = "anthropic";

    public LlmProperties {
        provider = provider == null || provider.isBlank() ? OLLAMA : provider.trim().toLowerCase(Locale.ROOT);
        if (!OLLAMA.equals(provider) && !ANTHROPIC.equals(provider)) {
            throw new IllegalArgumentException(
                    "celestia.llm.provider must be \"" + OLLAMA + "\" or \"" + ANTHROPIC + "\", got " + provider);
        }
        // base-url is Ollama's; the Claude API's endpoint is the SDK's own.
        if (OLLAMA.equals(provider)) {
            requireText(baseUrl, "celestia.llm.base-url");
        }
        if (ANTHROPIC.equals(provider) && maxTokens <= 0) {
            throw new IllegalArgumentException(
                    "celestia.llm.max-tokens must be positive for the anthropic provider, got " + maxTokens);
        }
        requireText(model, "celestia.llm.model");
        requireText(fallbackModel, "celestia.llm.fallback-model");
        if (maxToolRounds <= 0) {
            throw new IllegalArgumentException(
                    "celestia.llm.max-tool-rounds must be positive, got " + maxToolRounds
                            + " — a non-positive cap is not a cap (FR-010)");
        }
        if (maxConcurrentTurns <= 0) {
            throw new IllegalArgumentException(
                    "celestia.llm.max-concurrent-turns must be positive, got " + maxConcurrentTurns
                            + " — zero permits would answer nobody (FR-010a)");
        }
        if (turnDeadline == null || turnDeadline.isZero() || turnDeadline.isNegative()) {
            throw new IllegalArgumentException(
                    "celestia.llm.turn-deadline must be positive, got " + turnDeadline
                            + " — an unbounded turn is how a slow reply becomes an outage (FR-010)");
        }
    }

    private static void requireText(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " must be set");
        }
    }
}
