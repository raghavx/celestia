package com.celestia.agent.persistence;

import com.celestia.geo.BirthMomentFlag;
import com.celestia.geo.DatasetVersions;
import com.celestia.geo.ResolvedBirth;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;

/** Hand-built {@link ResolvedBirth} fixtures — no {@code timeshape}, no I/O. */
final class BirthFixtures {

    private BirthFixtures() {}

    /** Pune, 1985-06-15 14:30 IST — an ordinary case, no flags. */
    static ResolvedBirth pune1985() {
        return new ResolvedBirth(
                LocalDateTime.of(1985, 6, 15, 14, 30),
                ZoneId.of("Asia/Kolkata"),
                Instant.parse("1985-06-15T09:00:00Z"),
                ZoneOffset.ofHoursMinutes(5, 30),
                18.5204, 73.8567,
                "Pune, Maharashtra, India", "India", "fixture",
                null, true,
                EnumSet.noneOf(BirthMomentFlag.class),
                new DatasetVersions("2026a", "2025b.26"));
    }

    /** Unknown birth time → noon + TIME_NOT_KNOWN. */
    static ResolvedBirth puneUnknownTime() {
        return new ResolvedBirth(
                LocalDateTime.of(1970, 2, 20, 12, 0),
                ZoneId.of("Asia/Kolkata"),
                Instant.parse("1970-02-20T06:30:00Z"),
                ZoneOffset.ofHoursMinutes(5, 30),
                18.5204, 73.8567,
                "Pune", "India", "fixture",
                null, false,
                EnumSet.of(BirthMomentFlag.TIME_NOT_KNOWN),
                new DatasetVersions("2026a", "2025b.26"));
    }

    /** Stated zone differs from geocoded → ZONE_CONFLICT + a recorded stated zone. */
    static ResolvedBirth karachiZoneConflict() {
        return new ResolvedBirth(
                LocalDateTime.of(1990, 3, 15, 8, 0),
                ZoneId.of("Asia/Karachi"),
                Instant.parse("1990-03-15T03:00:00Z"),
                ZoneOffset.ofHours(5),
                24.8607, 67.0011,
                "Karachi", "Pakistan", "fixture",
                ZoneId.of("Asia/Kolkata"), true,
                EnumSet.of(BirthMomentFlag.ZONE_CONFLICT),
                new DatasetVersions("2026a", "2025b.26"));
    }
}
