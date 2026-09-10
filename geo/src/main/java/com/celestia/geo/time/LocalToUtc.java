package com.celestia.geo.time;

import com.celestia.geo.BirthMomentFlag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRules;
import java.time.zone.ZoneRulesProvider;
import java.util.EnumSet;
import java.util.List;
import java.util.NavigableMap;

/**
 * Local civil date/time + IANA zone to a UTC instant, using {@code java.time}'s
 * historical rules for that date (pre-1970 offsets, DST transitions, one-off
 * war-time changes).
 *
 * <p>Ambiguous or non-existent local times are <strong>flagged and resolved</strong>
 * by a documented rule, never silently guessed (research.md §3):
 *
 * <ul>
 *   <li>DST <b>gap</b> — the local time never occurred: shifted forward by the
 *       gap length ({@link BirthMomentFlag#DST_GAP}).</li>
 *   <li>DST <b>fold</b> — the local time occurred twice: the earlier offset
 *       ({@link BirthMomentFlag#DST_FOLD}).</li>
 *   <li>{@code time == null} — the birth time is unknown: {@code 12:00} local
 *       ({@link BirthMomentFlag#TIME_NOT_KNOWN}).</li>
 * </ul>
 *
 * <p>Pure — every input is explicit; no wall clock.
 */
public final class LocalToUtc {

    private LocalToUtc() {}

    /**
     * @param date the local civil date; non-null
     * @param time the local civil time; {@code null} ⇒ unknown ⇒ {@code 12:00}
     * @param zone the IANA zone; non-null
     */
    public static InstantResolution resolve(LocalDate date, LocalTime time, ZoneId zone) {
        if (date == null) {
            throw new IllegalArgumentException("date must not be null");
        }
        if (zone == null) {
            throw new IllegalArgumentException("zone must not be null");
        }

        EnumSet<BirthMomentFlag> flags = EnumSet.noneOf(BirthMomentFlag.class);
        LocalTime civil = time;
        if (civil == null) {
            civil = LocalTime.NOON;
            flags.add(BirthMomentFlag.TIME_NOT_KNOWN);
        }

        LocalDateTime ldt = LocalDateTime.of(date, civil);
        ZoneRules rules = zone.getRules();
        List<ZoneOffset> validOffsets = rules.getValidOffsets(ldt);

        ZonedDateTime zdt;
        if (validOffsets.size() == 1) {
            zdt = ldt.atZone(zone);
        } else if (validOffsets.isEmpty()) {
            // Spring-forward gap: atZone() returns the instant just after the gap.
            flags.add(BirthMomentFlag.DST_GAP);
            zdt = ldt.atZone(zone);
        } else {
            // Fall-back fold: two valid offsets; take the earlier (pre-transition) one.
            flags.add(BirthMomentFlag.DST_FOLD);
            zdt = ldt.atZone(zone).withEarlierOffsetAtOverlap();
        }

        return new InstantResolution(zdt.toInstant(), zdt.getOffset(), flags, currentTzdbVersion());
    }

    /**
     * The IANA {@code tzdb} release the running JRE's {@code java.time} rules were
     * built from. Global to the JRE — the same for every zone id — so a stable
     * reference zone is queried.
     */
    static String currentTzdbVersion() {
        NavigableMap<String, ZoneRules> versions = ZoneRulesProvider.getVersions("Etc/UTC");
        if (versions.isEmpty()) {
            throw new IllegalStateException("no tzdb version reported by the JRE");
        }
        return versions.lastKey();
    }
}
