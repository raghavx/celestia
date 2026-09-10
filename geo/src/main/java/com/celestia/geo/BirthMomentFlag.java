package com.celestia.geo;

/**
 * A caveat raised while resolving a birth moment. Every flag means "we produced a
 * best-effort answer; the caller (SPEC-010) may want to confirm before persisting".
 *
 * <p>{@code geo} never blocks on a flag — it resolves and records.
 */
public enum BirthMomentFlag {

    /** The birth time was not known; {@code 12:00} local was used (research.md §4). */
    TIME_NOT_KNOWN,

    /** The local time falls in a DST spring-forward gap and never occurred; the
     *  instant was shifted forward by the gap length (research.md §3). */
    DST_GAP,

    /** The local time falls in a DST fall-back fold and occurred twice; the
     *  earlier offset was used (research.md §3). */
    DST_FOLD,

    /** No timezone polygon contained the coordinate; a longitude-derived
     *  {@code Etc/GMT} zone was used (research.md §2). */
    ZONE_APPROXIMATED,

    /** The latitude is at or beyond the Placidus polar limit (ADR-0004). The
     *  birth moment still resolves; the <em>chart</em> pipeline rejects it at
     *  cast time (SPEC-002). */
    POLAR_LATITUDE,

    /** The caller supplied a zone that differs from the geocoded one. The
     *  geocoded zone was used; the stated zone is recorded on the result. */
    ZONE_CONFLICT
}
