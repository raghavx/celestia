package com.celestia.guardrail;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recognisably off-topic requests — recipes, code, small talk (research.md §3).
 *
 * <p><b>This buys diagnosis, not a decision.</b> Under fail-closed every message
 * it recognises would be refused anyway, because none of it carries in-scope
 * evidence. It exists so {@code OFF_TOPIC} can be counted apart from
 * {@code UNMATCHED}, and that distinction is the only instrument the product has
 * for judging whether failing closed is costing it real questions (FR-010,
 * SC-007).
 */
final class OffTopicPhrases {

    private static final String RESOURCE = "com/celestia/guardrail/offtopic-phrases.tsv";

    private final List<Entry> entries;

    private record Entry(String name, Pattern pattern) {}

    private OffTopicPhrases(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    static OffTopicPhrases bundled() {
        Tsv.Table table = Tsv.read(RESOURCE);
        int nameIndex = table.columnIndex("name");
        int patternIndex = table.columnIndex("pattern");
        if (nameIndex < 0 || patternIndex < 0) {
            throw new IllegalStateException(RESOURCE + " needs name and pattern columns");
        }
        List<Entry> entries = new ArrayList<>();
        for (List<String> record : table.records()) {
            String name = table.cell(record, nameIndex);
            String pattern = table.cell(record, patternIndex);
            if (name.isBlank() || pattern.isBlank()) {
                throw new IllegalStateException("off-topic phrase with an empty name or pattern: " + record);
            }
            entries.add(new Entry(
                    name, Pattern.compile("\\b(?:" + pattern + ")\\b", Pattern.CASE_INSENSITIVE)));
        }
        return new OffTopicPhrases(entries);
    }

    /** The name of the first recognised off-topic pattern, for the operator's view. */
    Optional<String> firstMatch(String normalised) {
        return entries.stream()
                .filter(entry -> entry.pattern().matcher(normalised).find())
                .findFirst()
                .map(Entry::name);
    }
}
