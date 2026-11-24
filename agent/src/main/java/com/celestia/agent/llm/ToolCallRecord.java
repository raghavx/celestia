package com.celestia.agent.llm;

import java.util.List;
import java.util.Objects;

/**
 * One tool call, as the turn record keeps it (FR-020, data-model.md
 * "ToolCallRecord"): which tool ran, what it was asked, and which sources came
 * back — behind their references, resolvable in the turn record and nowhere the
 * model can see.
 *
 * @param ordinal which round produced it, so a loop is visible in the record
 * @param tool the tool name
 * @param arguments the arguments as the model supplied them — <b>never a
 *     {@code userId}, a coordinate or a clock</b>, because all three are injected
 *     server-side and no tool has a parameter for them (FR-006)
 * @param references the references this call put in front of the model
 * @param truncated whether SPEC-010's caps dropped results that existed
 */
public record ToolCallRecord(
        int ordinal, String tool, String arguments, List<String> references, boolean truncated) {

    public ToolCallRecord {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(arguments, "arguments");
        references = List.copyOf(references);
    }

    /** The same call with its references attached, once the adapter has minted them. */
    public ToolCallRecord withReferences(List<String> refs, boolean wasTruncated) {
        return new ToolCallRecord(ordinal, tool, arguments, refs, wasTruncated);
    }
}
