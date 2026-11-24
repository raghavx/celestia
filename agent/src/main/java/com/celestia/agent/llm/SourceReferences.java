package com.celestia.agent.llm;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;

/**
 * The per-turn mapping that keeps the toolkit's own strings out of the model's
 * context (FR-013a–d, research.md §3).
 *
 * <p>Every tool result's {@link SourceRef} is swapped for a <b>reference</b> —
 * {@code s1}, {@code s2}, … — before the result is serialised for the model. The
 * real sources go to the turn record, where the evaluation harness and a human
 * reviewer can resolve them.
 *
 * <p><b>An index, never an encoding.</b> No hash, no slug, no base64: a
 * reversible reference would put the sheet id back in the model's context by
 * another route (FR-013c). The reference is the source's position in first-seen
 * order and carries no information about it whatsoever.
 *
 * <p><b>Stable within a turn.</b> The same source seen twice returns the same
 * reference, so the model can still recognise that two findings rest on the same
 * rule — SPEC-010 {@code agent-tools.md} obligation 3 survives the swap
 * (FR-013c).
 *
 * <p><b>Scoped to one turn.</b> A new instance per turn, discarded with it. A
 * reference that persisted would be a small, long-lived map of the toolkit's
 * structure, which is more to protect than the problem needs (spec.md
 * Assumptions).
 *
 * <p>Not thread-safe, and does not need to be: a turn is answered on one thread
 * and only one turn runs at a time (FR-010a).
 */
public final class SourceReferences {

    private static final String PREFIX = "s";

    private final SequencedMap<SourceRef, String> bySource = new LinkedHashMap<>();
    private final SequencedMap<String, SourceRef> bySReference = new LinkedHashMap<>();

    /**
     * The reference for {@code source}, minting one on first sight.
     *
     * @return {@code s1}, {@code s2}, … — the same value for the same source
     *     within this turn
     */
    public String reference(SourceRef source) {
        Objects.requireNonNull(source, "source");
        String existing = bySource.get(source);
        if (existing != null) {
            return existing;
        }
        String minted = PREFIX + (bySource.size() + 1);
        bySource.put(source, minted);
        bySReference.put(minted, source);
        return minted;
    }

    /** The real source behind {@code reference}, for the turn record and review (FR-013b). */
    public Optional<SourceRef> resolve(String reference) {
        return Optional.ofNullable(bySReference.get(reference));
    }

    /** Every reference minted this turn, in first-seen order, with its real source. */
    public Map<String, SourceRef> resolved() {
        return Map.copyOf(bySReference);
    }

    /** The references minted so far, in order — so a tool can report the ones it just produced. */
    public List<String> references() {
        return List.copyOf(bySReference.sequencedKeySet());
    }

    /** The references minted after the first {@code count} — what one tool call contributed. */
    public List<String> referencesSince(int count) {
        List<String> all = references();
        return count >= all.size() ? List.of() : List.copyOf(all.subList(count, all.size()));
    }

    public int size() {
        return bySource.size();
    }
}
