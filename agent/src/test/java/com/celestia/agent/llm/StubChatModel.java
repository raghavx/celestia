package com.celestia.agent.llm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * A chat model that answers from a script, <b>and runs the tool loop the way the
 * real one does</b>.
 *
 * <p>This is where correctness lives (plan.md "Testing", tier 1): the wiring, the
 * reference swap, statelessness, the budgets and the leak scan are all provable
 * without Ollama, and a result from a real 0.5B model would prove none of them.
 *
 * <p><b>It does not emulate a tool loop, and that is the point.</b> An earlier
 * version did: it executed each round and re-entered itself, on the assumption
 * that Spring AI's chat models run the loop internally. They do not — they return
 * a tool-call response with empty text and leave the round to the caller — so the
 * emulation encoded an assumption rather than the framework's behaviour, and every
 * test passed while production could not have worked. A stub must be wrong in the
 * same direction as reality or it is worse than no stub.
 *
 * <p>It <b>keeps every prompt it was given</b>, which is what lets a test assert
 * on what the model received rather than on what it replied — the only honest way
 * to check that no toolkit string reached it (SC-010).
 */
public final class StubChatModel implements ChatModel {

    private final Deque<Object> script = new ArrayDeque<>();
    private final List<Prompt> seen = new ArrayList<>();

    public StubChatModel() {}

    /** Reply with this text and stop. */
    public StubChatModel thenAnswer(String text) {
        script.add(text);
        return this;
    }

    /** Ask for these tool calls, then continue with the rest of the script. */
    public StubChatModel thenCallTools(AssistantMessage.ToolCall... calls) {
        script.add(List.of(calls));
        return this;
    }

    /** Ask for the same single tool call {@code times} times over — a model that loops. */
    public StubChatModel thenLoopOn(String tool, String argumentsJson, int times) {
        for (int i = 0; i < times; i++) {
            thenCallTools(toolCall(tool, argumentsJson));
        }
        return this;
    }

    /** Fail the way an unreachable model, or one emitting unreadable tool JSON, fails. */
    public StubChatModel thenFail(RuntimeException failure) {
        script.add(failure);
        return this;
    }

    public List<Prompt> promptsSeen() {
        return List.copyOf(seen);
    }

    /**
     * Everything the model was ever shown, across every round and call —
     * <b>including the tool results</b>.
     *
     * <p>Tool results arrive as a {@code ToolResponseMessage}, whose
     * {@code getText()} is empty: the payload is in its responses. A check that
     * only read {@code getText()} would pass while every sheet id in the product
     * sat in the model's context, which is the opposite of what SC-010 asserts.
     */
    public String everythingSeen() {
        StringBuilder out = new StringBuilder();
        for (Prompt prompt : seen) {
            for (var message : prompt.getInstructions()) {
                out.append(message.getText()).append('\n');
                if (message instanceof ToolResponseMessage tools) {
                    for (ToolResponseMessage.ToolResponse response : tools.getResponses()) {
                        out.append(response.name()).append(' ').append(response.responseData()).append('\n');
                    }
                }
            }
        }
        return out.toString();
    }

    public int callCount() {
        return seen.size();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        seen.add(prompt);
        Object next = script.poll();
        if (next == null) {
            throw new IllegalStateException("the stub model ran out of script after " + seen.size() + " calls");
        }
        if (next instanceof RuntimeException failure) {
            throw failure;
        }
        if (next instanceof String text) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        }

        @SuppressWarnings("unchecked")
        List<AssistantMessage.ToolCall> calls = (List<AssistantMessage.ToolCall>) next;
        ChatResponse response = new ChatResponse(List.of(new Generation(
                AssistantMessage.builder().content("").toolCalls(calls).build())));
        // Returned as the real models return it: tool calls present, text empty,
        // and the ROUND NOT RUN. This stub used to execute the round and re-enter
        // itself, emulating a loop Spring AI does not provide — which made the
        // tests pass while production could not work at all (T044). The loop
        // belongs to OwnerAnswerer, so the stub must not have one.
        return response;
    }

    @Override
    public ToolCallingChatOptions getDefaultOptions() {
        return ToolCallingChatOptions.builder().build();
    }

    /** A tool call as a model would emit one. */
    public static AssistantMessage.ToolCall toolCall(String name, String argumentsJson) {
        return new AssistantMessage.ToolCall("call-" + name, "function", name, argumentsJson);
    }
}
