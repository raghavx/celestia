package com.celestia.core.toolkit;

import java.util.List;

/**
 * One sheet of the licensed toolkit, stored complete (SPEC-010 FR-001).
 *
 * <p>Every non-empty row is kept in workbook order, word for word
 * (Constitution X), which is what lets a procedure be followed in its written
 * order (FR-007) and guarantees nothing was silently dropped on import.
 *
 * <p>{@code id} is assigned by hand in the import manifest, not derived from the
 * title, and is stable across toolkit releases. Renaming one is a breaking
 * change that needs an ADR (contracts/core-api.md "Stability").
 *
 * <p><b>Internal identity.</b> {@code id} and {@code title} name a licensed
 * asset used to build Celestia. They are for traceability — logging, the eval
 * harness, human review — and MUST NOT appear in a reply to a user
 * (Constitution X, ADR-0025, FR-008).
 *
 * @param order 1-based position in the workbook
 * @param id stable slug, unique across sheets
 * @param title the workbook's own sheet title
 * @param group the subject it belongs to
 * @param kind the shape of its content
 * @param sensitivity the sheet's default class; a rule may be raised above it
 * @param answerable false for housekeeping and author-"ignore" sheets, which are
 *     stored and browsable by id but never used as answer content (FR-014)
 * @param rows every non-empty row, in order, trailing empty cells trimmed
 */
public record ToolkitSheet(
        int order,
        String id,
        String title,
        TopicGroup group,
        SheetKind kind,
        Sensitivity sensitivity,
        boolean answerable,
        List<List<String>> rows) {

    public ToolkitSheet {
        if (order < 1) {
            throw new IllegalArgumentException("sheet order must be >= 1, got " + order);
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("sheet id must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("sheet title must not be blank");
        }
        rows = rows.stream().map(List::copyOf).toList();
    }

    /** How many rows this sheet holds. */
    public int rowCount() {
        return rows.size();
    }

    /** True when any cell of any row contains {@code term}, case-insensitively. */
    boolean containsTerm(String term) {
        String needle = term.toLowerCase();
        for (List<String> row : rows) {
            for (String cell : row) {
                if (cell.toLowerCase().contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }
}
