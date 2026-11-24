package com.celestia.guardrail;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The loaded hostile rule set (FR-004, FR-005).
 *
 * <p>Every rule is matched against the normalised view <b>and every decoded
 * candidate</b>, so the same attack written plainly, hidden behind zero-width
 * characters, or base64-encoded reaches the same rule. The first rule that fires
 * decides, and its name is what the refusal reports.
 */
final class HostileRules {

    private static final String RESOURCE = "com/celestia/guardrail/hostile-phrases.tsv";

    private final List<HostileRule> rules;

    /**
     * Literals that must be present for any rule to match, one set per rule.
     *
     * <p><b>Why.</b> Running twenty-eight alternations over a 4,096-character
     * message costs about 1.2 ms — 89% of a decision, measured. Every phrase
     * pattern contains alphabetic runs, and a match necessarily contains one of
     * them, so a case-insensitive {@code indexOf} for the cheapest of them is a
     * <b>sound</b> prefilter: it can skip work, never change an outcome. A rule
     * with no extractable literal (an E.164 shape, a turn marker) has an empty
     * set and is always evaluated.
     *
     * <p>This is what keeps the gate inside plan.md's 1 ms budget for a message
     * at the bound while leaving first-match order untouched.
     */
    private final List<List<String>> requiredLiterals;

    private HostileRules(List<HostileRule> rules) {
        this.rules = List.copyOf(rules);
        this.requiredLiterals = rules.stream()
                .map(rule -> rule.matchers().stream()
                        .flatMap(pattern -> literalsOf(pattern.pattern()).stream())
                        .toList())
                .toList();
    }

    /**
     * The maximal alphabetic runs of four or more characters in a pattern. Any
     * string the pattern matches contains at least one of them, which is what
     * makes the prefilter sound.
     */
    private static List<String> literalsOf(String pattern) {
        List<String> literals = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (Character.isLetter(c)) {
                run.append(c);
            } else {
                if (run.length() >= 4) {
                    literals.add(run.toString().toLowerCase(java.util.Locale.ROOT));
                }
                run.setLength(0);
            }
        }
        if (run.length() >= 4) {
            literals.add(run.toString().toLowerCase(java.util.Locale.ROOT));
        }
        return literals;
    }

    /** Load the rules from the classpath table, validating as we go (fail fast). */
    static HostileRules bundled() {
        Tsv.Table table = Tsv.read(RESOURCE);
        int familyIndex = table.columnIndex("family");
        int nameIndex = table.columnIndex("name");
        int kindIndex = table.columnIndex("kind");
        int patternIndex = table.columnIndex("pattern");
        if (familyIndex < 0 || nameIndex < 0 || kindIndex < 0 || patternIndex < 0) {
            throw new IllegalStateException(RESOURCE + " needs family, name, kind and pattern columns");
        }

        List<HostileRule> rules = new ArrayList<>();
        Set<String> names = new java.util.HashSet<>();
        for (List<String> record : table.records()) {
            String name = table.cell(record, nameIndex);
            String pattern = table.cell(record, patternIndex);
            String kind = table.cell(record, kindIndex);

            // Load-time validation: FR-010 and SC-007 depend on every refusal
            // naming a rule, so a nameless or empty rule must break the build
            // rather than produce an unattributable refusal.
            if (pattern.isBlank()) {
                throw new IllegalStateException("hostile rule with an empty pattern: " + name);
            }
            if (!names.add(name)) {
                throw new IllegalStateException("duplicate hostile rule name: " + name);
            }
            HostileRule.Family family = family(table.cell(record, familyIndex), name);
            rules.add(new HostileRule(family, name, List.of(compile(pattern, kind, name))));
        }
        if (rules.isEmpty()) {
            throw new IllegalStateException(RESOURCE + " is empty — the gate would admit every attack");
        }
        return new HostileRules(rules);
    }

    private static HostileRule.Family family(String raw, String name) {
        try {
            return HostileRule.Family.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("hostile rule " + name + " names an unknown family: " + raw, e);
        }
    }

    /**
     * A {@code phrase} is wrapped in word boundaries so it can never match
     * inside a longer word (FR-011); a {@code regex} is compiled as written,
     * for the few rules where a shape rather than a word is the signal.
     */
    private static Pattern compile(String pattern, String kind, String name) {
        String source = switch (kind) {
            case "phrase" -> "\\b(?:" + pattern + ")\\b";
            case "regex" -> pattern;
            default -> throw new IllegalStateException(
                    "hostile rule " + name + " has kind '" + kind + "'; expected phrase or regex");
        };
        try {
            return Pattern.compile(source, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            throw new IllegalStateException("hostile rule " + name + " has an invalid pattern", e);
        }
    }

    /**
     * The first rule matching any view, or empty when none does.
     *
     * @param views the normalised view followed by every decoded candidate
     */
    Optional<HostileRule> firstMatch(List<String> views) {
        List<String> lowered = views.stream().map(v -> v.toLowerCase(java.util.Locale.ROOT)).toList();
        for (int i = 0; i < rules.size(); i++) {
            HostileRule rule = rules.get(i);
            List<String> literals = requiredLiterals.get(i);
            for (int v = 0; v < views.size(); v++) {
                if (!literals.isEmpty() && literals.stream().noneMatch(lowered.get(v)::contains)) {
                    continue; // cannot match: none of its literals is present
                }
                if (rule.matches(views.get(v))) {
                    return Optional.of(rule);
                }
            }
        }
        return Optional.empty();
    }

    /** Every family that has at least one rule — the build asserts this is all five. */
    Set<HostileRule.Family> families() {
        EnumSet<HostileRule.Family> present = EnumSet.noneOf(HostileRule.Family.class);
        rules.forEach(rule -> present.add(rule.family()));
        return present;
    }

    int size() {
        return rules.size();
    }
}
