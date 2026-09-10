package com.celestia.core.prediction;

/**
 * The v1 daily-reading verdict for one matter. See {@code core/REFERENCES.md} and
 * {@code specs/006-daily-prediction/research.md} §5 for the exact rule.
 *
 * <ul>
 *   <li>{@link #FAVOURABLE} — the matter's favourable houses are dasha-activated
 *       and a transit triggers them, and the obstructive side does not outweigh.</li>
 *   <li>{@link #MIXED} — activated but not (cleanly) triggered, or evenly poised.</li>
 *   <li>{@link #UNFAVOURABLE} — the obstructive side is more strongly activated.</li>
 *   <li>{@link #QUIET} — neither side is dasha-activated today.</li>
 * </ul>
 */
public enum Verdict {
    FAVOURABLE,
    MIXED,
    UNFAVOURABLE,
    QUIET
}
