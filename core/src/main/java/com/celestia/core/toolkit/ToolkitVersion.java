package com.celestia.core.toolkit;

/**
 * Which release of the licensed toolkit produced the bundled resources
 * (SPEC-010 data-model.md "ToolkitRelease").
 *
 * <p>{@link #id()} joins the engine version (research.md &sect;7), so importing a
 * new toolkit release invalidates every cached chart and reading — a stored
 * answer always stays explainable by the toolkit that produced it (FR-009,
 * Constitution III).
 *
 * @param title the workbook's own title
 * @param version the release date of the workbook, e.g. {@code 2026-09-12}
 * @param sheets how many sheets were imported; must equal the manifest row count
 * @param rules how many promise rules were extracted
 * @param sourceSha256 64-hex fingerprint of the workbook, so a reading can be
 *     traced to the exact file it came from even though that file is never
 *     committed
 */
public record ToolkitVersion(String title, String version, int sheets, int rules, String sourceSha256) {

    public ToolkitVersion {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("toolkit title must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("toolkit version must not be blank");
        }
        if (sheets < 0 || rules < 0) {
            throw new IllegalArgumentException("sheet and rule counts must not be negative");
        }
        if (sourceSha256 == null || !sourceSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sourceSha256 must be 64 lower-case hex characters");
        }
    }

    /** {@code toolkit-<version>} — the tag that joins the engine version. */
    public String id() {
        return "toolkit-" + version;
    }
}
