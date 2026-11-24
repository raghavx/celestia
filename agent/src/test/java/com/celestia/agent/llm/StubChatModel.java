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
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;

/**
 * A chat model that answers from a script, <b>and runs the tool loop the way the
 * real one does</b>.
 *
 * <p>This is where correctness lives (plan.md "Testing", tier 1): the wiring, the
 * reference swap, statelessness, the budgets and the leak scan are all provable
 * without Ollama, and a result from a real 0.5B model would prove none of them.
 *
 * <p><b>Why it emulates the loop.</b> In Spring AI 2.0 the tool loop is inside the
 * chat model, which calls a {@link ToolCallingManager} once per round. A stub that
 * merely returned a tool-call response would never exercise
 * {@code TurnToolCallingManager} — so the round cap, the deadline and the
 * per-call records would all go untested. Given a manager, this stub executes the
 * calls and re-enters itself with the enlarged history, exactly as
 * {@code OllamaChatModel} does.
 *
 * <p>It <b>keeps every prompt it was given</b>, which is what lets a test assert
 * on what the model received rather than on what it replied — the only honest way
 * to check that no toolkit string reached it (SC-010).
 */
public final class StubChatModel implements ChatModel {

    private final Deque<Object> script = new ArrayDeque<>();
    private final List<Prompt> seen = new ArrayList<>();
    private final ToolCallingManager toolCallingManager;

    /** A model with no tools: it can only answer or fail. */
    public StubChatModel() {
        this(null);
    }

    /** A model that executes tool calls through {@code manager}, like the real one. */
    public StubChatModel(ToolCallingManager manager) {
        this.toolCallingManager = manager;
    }

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
        if (toolCallingManager == null) {
            return response;
        }
        // What OllamaChatModel does: execute the round, then go round again with the
        // tool results appended to the conversation.
        ToolExecutionResult executed = toolCallingManager.executeToolCalls(prompt, response);
        return call(new Prompt(executed.conversationHistory(), prompt.getOptions()));
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
