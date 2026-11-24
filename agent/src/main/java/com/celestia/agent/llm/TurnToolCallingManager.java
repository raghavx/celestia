package com.celestia.agent.llm;

import java.time.Clock;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * The seam that makes a turn <b>bounded</b> (FR-010) and <b>reviewable</b>
 * (FR-020).
 *
 * <p>Spring AI's chat models do not execute tools — they return a tool-call
 * response and leave the round to the caller — so {@link OwnerAnswerer} owns the
 * loop and <b>counts the rounds</b>. What belongs here is what only this seam
 * sees: the arguments the model actually supplied, recorded as the call is
 * executed, and a deadline check on each round.
 *
 * <p><b>The cap and the deadline are separate</b> on purpose: a round-cap hit is
 * a prompt defect and a timeout is a hosting one, so collapsing them into one
 * number would make the wrong team look (contracts/answerer-api.md).
 *
 * <p>Per-turn state lives in {@link TurnScope}, bound to the answering thread.
 * This manager is a singleton and holds none.
 */
public final class TurnToolCallingManager implements ToolCallingManager {

    private final ToolCallingManager delegate;
    private final Clock clock;

    public TurnToolCallingManager(ToolCallingManager delegate, Clock clock) {
        this.delegate = delegate;
        this.clock = clock;
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions options) {
        return delegate.resolveToolDefinitions(options);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse response) {
        TurnScope scope = TurnScope.current();
        scope.checkDeadline(clock.instant());

        AssistantMessage message = response.getResult().getOutput();
        for (AssistantMessage.ToolCall call : message.getToolCalls()) {
            scope.beginCall(call.name(), call.arguments() == null ? "{}" : call.arguments());
        }

        ToolExecutionResult result = delegate.executeToolCalls(prompt, response);
        scope.checkDeadline(clock.instant());
        return result;
    }
}
