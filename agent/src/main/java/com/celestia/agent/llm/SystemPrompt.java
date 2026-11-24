package com.celestia.agent.llm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The hardened system prompt, loaded once (SPEC-012 T014, research.md §5).
 *
 * <p><b>A classpath resource versioned with the code</b>, not a property and not a
 * database row: it is the only thing holding several NON-NEGOTIABLE obligations,
 * so it belongs under the same review as code. A prompt editable in production is
 * a guardrail editable in production.
 *
 * <p>Fails fast when it is missing or empty. A prompt missing its leak
 * prohibition is a guardrail missing a wall, and an application that starts
 * without one would answer with no constraints at all.
 */
@Component
public class SystemPrompt {

    static final String RESOURCE = "com/celestia/agent/llm/system-prompt.md";

    private final String text;

    public SystemPrompt() {
        this(RESOURCE);
    }

    SystemPrompt(String resource) {
        this.text = read(resource);
        if (text.isBlank()) {
            throw new IllegalStateException(resource + " is empty — refusing to run an unconstrained model");
        }
    }

    /** The prompt as the model receives it. */
    public String text() {
        return text;
    }

    private static String read(String resource) {
        ClassPathResource classpath = new ClassPathResource(resource);
        if (!classpath.exists()) {
            throw new IllegalStateException(
                    resource + " is missing — the system prompt is versioned with the code "
                            + "and the application must not start without it (research.md §5)");
        }
        try (var in = classpath.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + resource, e);
        }
    }
}
