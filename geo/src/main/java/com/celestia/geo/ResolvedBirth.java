package com.celestia.geo;

import com.celestia.ephemeris.BirthData;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;

/**
 * A birth resolved to the exact inputs a chart needs, plus everything the
 * {@code birth_data} row persists that is <em>derived from the place and time</em>.
 *
 * <p>The two non-derived columns — {@code place_query} (the raw text the user
 * typed) and {@code name} — are threaded to persistence by the caller (SPEC-010),
 * not carried here (FR-012).
 *
 * @param localDateTime the civil birth date + time (noon if the time was unknown)
 * @param zoneId the zone used — the geocoded zone, unless it had no polygon
 * @param instant the UTC instant
 * @param offsetApplied the offset in effect at {@code instant}
 * @param latitude from the chosen candidate; {@code |latitude| <= 90}
 * @param longitude from the chosen candidate; {@code |longitude| <= 180}
 * @param placeLabel the candidate's display name
 * @param country the candidate's country
 * @param geocodeSource the candidate's source ({@code "opencage"} / {@code "fixture"} / {@code "direct-input"})
 * @param statedZoneId the caller-supplied zone, only when it differed from the
 *     geocoded one (⇔ {@link BirthMomentFlag#ZONE_CONFLICT}); else {@code null}
 * @param birthTimeKnown {@code false} ⇔ {@link BirthMomentFlag#TIME_NOT_KNOWN}
 * @param flags every caveat raised while resolving
 * @param versions the {@code tzdb} + timezone-boundary dataset versions (FR-016)
 */
public record ResolvedBirth(
        LocalDateTime localDateTime,
        ZoneId zoneId,
        Instant instant,
        ZoneOffset offsetApplied,
        double latitude,
        double longitude,
        String placeLabel,
        String country,
        String geocodeSource,
        ZoneId statedZoneId,
        boolean birthTimeKnown,
        EnumSet<BirthMomentFlag> flags,
        DatasetVersions versions) {

    public ResolvedBirth {
        if (localDateTime == null || zoneId == null || instant == null || offsetApplied == null) {
            throw new IllegalArgumentException("localDateTime, zoneId, instant, offsetApplied must be non-null");
        }
        if (placeLabel == null || placeLabel.isBlank()) {
            throw new IllegalArgumentException("placeLabel must not be blank");
        }
        if (geocodeSource == null || geocodeSource.isBlank()) {
            throw new IllegalArgumentException("geocodeSource must not be blank");
        }
        if (versions == null) {
            throw new IllegalArgumentException("versions must not be null");
        }
        if (!(Math.abs(latitude) <= 90.0)) {
            throw new IllegalArgumentException("latitude out of [-90, 90]: " + latitude);
        }
        if (!(Math.abs(longitude) <= 180.0)) {
            throw new IllegalArgumentException("longitude out of [-180, 180]: " + longitude);
        }
        flags = flags == null ? EnumSet.noneOf(BirthMomentFlag.class) : EnumSet.copyOf(flags);
        if (birthTimeKnown == flags.contains(BirthMomentFlag.TIME_NOT_KNOWN)) {
            throw new IllegalArgumentException("birthTimeKnown must be the negation of the TIME_NOT_KNOWN flag");
        }
        if ((statedZoneId != null) != flags.contains(BirthMomentFlag.ZONE_CONFLICT)) {
            throw new IllegalArgumentException("statedZoneId is set iff the ZONE_CONFLICT flag is present");
        }
        country = country == null ? "" : country;
    }

    /** The {@code (instant, latitude, longitude)} a natal chart for this birth consumes. */
    public BirthData birthData() {
        return new BirthData(instant, latitude, longitude);
    }

    /** A defensive copy — callers must not mutate the stored set. */
    public EnumSet<BirthMomentFlag> flags() {
        return EnumSet.copyOf(flags);
    }
}
