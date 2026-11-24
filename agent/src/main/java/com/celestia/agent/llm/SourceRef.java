package com.celestia.agent.llm;

import java.util.Objects;

/**
 * A real toolkit source: the sheet it came from and the row within it. SPEC-010's
 * {@code source} on every tool result.
 *
 * <p><b>These three strings must never reach the model.</b> They are swapped for
 * an opaque reference before serialisation (FR-013a) and resolved again only in
 * the turn record, for the evaluation harness, the logs and human review
 * (FR-013b). Constitution X is NON-NEGOTIABLE about the reply, and this type is
 * the thing the reply must never contain.
 *
 * @param sheetId the toolkit sheet's id
 * @param sheetTitle the sheet's title
 * @param row the 1-based row, or 0 when the finding is the whole sheet
 */
public record SourceRef(String sheetId, String sheetTitle, int row) {

    public SourceRef {
        Objects.requireNonNull(sheetId, "sheetId");
        Objects.requireNonNull(sheetTitle, "sheetTitle");
        if (row < 0) {
            throw new IllegalArgumentException("row must not be negative: " + row);
        }
    }

    /** A source naming a sheet but no particular row. */
    public static SourceRef ofSheet(String sheetId, String sheetTitle) {
        return new SourceRef(sheetId, sheetTitle, 0);
    }
}
