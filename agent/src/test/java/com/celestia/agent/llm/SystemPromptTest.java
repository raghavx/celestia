package com.celestia.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SPEC-012 T026: every obligation of contracts/system-prompt.md is present.
 *
 * <p>A prompt missing its leak prohibition is a guardrail missing a wall, and this
 * test is what makes that a build failure rather than a discovery.
 */
class SystemPromptTest {

    private static String prompt;

    @BeforeAll
    static void load() {
        prompt = new SystemPrompt().text().toLowerCase(Locale.ROOT);
    }

    @Test
    @DisplayName("1 — speaks as a KP astrologer, in houses, lords, stars and timing (FR-012)")
    void obligationOne() {
        assertThat(prompt).contains("krishnamurti paddhati (kp) astrologer");
        assertThat(prompt).contains("houses").contains("sub lord").contains("timing");
    }

    @Test
    @DisplayName("2 — never names the source of its knowledge, references included (FR-012, FR-013)")
    void obligationTwo() {
        assertThat(prompt).contains("never name");
        assertThat(prompt).contains("sheet").contains("row").contains("workbook");
        // The prohibition covers the references too. "per s3" discloses nothing
        // about the toolkit, yet reads as a system quoting a document — which is
        // what Constitution X's "the agent IS the astrologer" is about.
        assertThat(prompt).contains("s1").contains("per s3");
    }

    @Test
    @DisplayName("3 — states nothing a tool result does not support (FR-014)")
    void obligationThree() {
        assertThat(prompt).contains("no basis");
        assertThat(prompt).contains("tool result");
    }

    @Test
    @DisplayName("4 — the toolkit's answer obligations (FR-015)")
    void obligationFour() {
        assertThat(prompt).contains("condition_to_check").contains("condition to check");
        assertThat(prompt).contains("partly_indicated").contains("partly indicated");
        assertThat(prompt).contains("once");
        assertThat(prompt).contains("retrograde").contains("weakened or delayed");
    }

    @Test
    @DisplayName("5 — the out-of-scope marker, exactly as SPEC-011 reads it (FR-014)")
    void obligationFive() {
        // A literal: OutputStage.OUT_OF_SCOPE_MARKER is package-private, so the
        // prompt and the guardrail agree by convention rather than by compilation.
        // If SPEC-011 ever changes the marker, this test is the warning.
        assertThat(new SystemPrompt().text()).contains("<<OUT_OF_SCOPE>>");
    }

    @Test
    @DisplayName("6 — never writes an advisory, a disclaimer or a helpline (FR-016)")
    void obligationSix() {
        assertThat(prompt).contains("never write an advisory");
        assertThat(prompt).contains("disclaimer").contains("helpline");
    }

    @Test
    @DisplayName("7 — computes nothing (FR-008)")
    void obligationSeven() {
        assertThat(prompt).contains("compute nothing");
        assertThat(prompt).contains("comes from a tool");
    }

    @Test
    @DisplayName("8 — cannot see earlier messages, and asks when a question cannot stand alone")
    void obligationEight() {
        assertThat(prompt).contains("no memory");
        assertThat(prompt).contains("ask what is meant");
        // FR-018a: the other direction. A question that names its own matter is
        // answerable as it stands, or the agent would ask about everything.
        assertThat(prompt).contains("already in front of you");
    }

    @Test
    @DisplayName("9 — keeps the reply inside one message, finishing its sentences (FR-017)")
    void obligationNine() {
        assertThat(prompt).contains("single whatsapp message");
        assertThat(prompt).contains("finish your sentences");
    }

    @Test
    void aMissingPromptStopsTheApplicationRatherThanRunningUnconstrained() {
        assertThatThrownBy(() -> new SystemPrompt("com/celestia/agent/llm/no-such-prompt.md"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }
}
