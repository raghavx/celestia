package com.celestia.geo;

import com.celestia.geo.place.PlaceCandidate;
import com.celestia.geo.time.InstantResolution;
import com.celestia.geo.time.LocalToUtc;
import com.celestia.geo.time.TimeZoneResolver;
import com.celestia.geo.time.ZoneResolution;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;

/**
 * Composes a chosen place candidate and a local birth date/time into a
 * {@link ResolvedBirth} (spec US3; research.md §7).
 *
 * <p>Pure — the only collaborator is a {@link TimeZoneResolver}; no clock, no
 * network. Deterministic: equal inputs ⇒ equal {@code ResolvedBirth}.
 */
public final class BirthMomentResolver {

    /**
     * Absolute latitude at or beyond which Placidus house cusps are undefined
     * (ADR-0004). Mirrors {@code SwissEphemerisConfig}'s default polar limit —
     * {@code geo} flags such a birth; the chart pipeline rejects it at cast time.
     */
    public static final double POLAR_LIMIT_DEG = 66.0;

    private final TimeZoneResolver zones;

    public BirthMomentResolver(TimeZoneResolver zones) {
        this.zones = java.util.Objects.requireNonNull(zones, "zones");
    }

    /**
     * @param place the candidate the user picked (or a {@code direct-input} one)
     * @param birthDate the local civil date; non-null
     * @param birthTime the local civil time; {@code null} ⇒ unknown ⇒ noon
     * @param statedZone a zone the user asserted, or {@code null}
     */
    public ResolvedBirth resolve(
            PlaceCandidate place, LocalDate birthDate, LocalTime birthTime, ZoneId statedZone) {
        java.util.Objects.requireNonNull(place, "place");
        java.util.Objects.requireNonNull(birthDate, "birthDate");

        EnumSet<BirthMomentFlag> flags = EnumSet.noneOf(BirthMomentFlag.class);

        ZoneResolution zr = zones.resolve(place.latitude(), place.longitude());
        if (zr.approximated()) {
            flags.add(BirthMomentFlag.ZONE_APPROXIMATED);
        }

        ZoneId effectiveZone = zr.zone();
        ZoneId recordedStatedZone = null;
        if (statedZone != null && !statedZone.equals(effectiveZone)) {
            flags.add(BirthMomentFlag.ZONE_CONFLICT);
            recordedStatedZone = statedZone;
        }

        if (Math.abs(place.latitude()) >= POLAR_LIMIT_DEG) {
            flags.add(BirthMomentFlag.POLAR_LATITUDE);
        }

        InstantResolution ir = LocalToUtc.resolve(birthDate, birthTime, effectiveZone);
        flags.addAll(ir.flags());

        LocalTime civil = birthTime != null ? birthTime : LocalTime.NOON;
        LocalDateTime local = LocalDateTime.of(birthDate, civil);
        boolean birthTimeKnown = !flags.contains(BirthMomentFlag.TIME_NOT_KNOWN);

        return new ResolvedBirth(
                local,
                effectiveZone,
                ir.instant(),
                ir.offsetApplied(),
                place.latitude(),
                place.longitude(),
                place.displayName(),
                place.country(),
                place.source(),
                recordedStatedZone,
                birthTimeKnown,
                flags,
                new DatasetVersions(ir.tzdbVersion(), zr.datasetVersion()));
    }
}
