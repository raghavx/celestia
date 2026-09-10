package com.celestia.geo.time;

import com.celestia.geo.BirthMomentFlag;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;

/**
 * The outcome of converting a local date/time + zone to a UTC instant.
 *
 * @param instant the UTC instant
 * @param offsetApplied the zone offset in effect at {@code instant} — shows which
 *     side of a DST transition was taken
 * @param flags {@link BirthMomentFlag#TIME_NOT_KNOWN}, {@link BirthMomentFlag#DST_GAP}
 *     or {@link BirthMomentFlag#DST_FOLD} as applicable (research.md §3, §4)
 * @param tzdbVersion the IANA {@code tzdb} release the rules came from
 */
public record InstantResolution(
        Instant instant, ZoneOffset offsetApplied, EnumSet<BirthMomentFlag> flags, String tzdbVersion) {

    public InstantResolution {
        if (instant == null) {
            throw new IllegalArgumentException("instant must not be null");
        }
        if (offsetApplied == null) {
            throw new IllegalArgumentException("offsetApplied must not be null");
        }
        if (tzdbVersion == null || tzdbVersion.isBlank()) {
            throw new IllegalArgumentException("tzdbVersion must not be blank");
        }
        flags = flags == null ? EnumSet.noneOf(BirthMomentFlag.class) : EnumSet.copyOf(flags);
    }

    /** A defensive copy — callers must not mutate the stored set. */
    public EnumSet<BirthMomentFlag> flags() {
        return EnumSet.copyOf(flags);
    }
}
