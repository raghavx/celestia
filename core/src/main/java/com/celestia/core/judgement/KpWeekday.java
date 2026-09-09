package com.celestia.core.judgement;

import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.SunriseProvider;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * The KP weekday, whose day lord rules from local <b>sunrise</b> to the next local
 * sunrise (not midnight).
 *
 * <p>Sun→Sunday, Moon→Monday, Mars→Tuesday, Mercury→Wednesday, Jupiter→Thursday,
 * Venus→Friday, Saturn→Saturday. Source: {@code core/REFERENCES.md}.
 */
public enum KpWeekday {
    SUNDAY(Graha.SUN),
    MONDAY(Graha.MOON),
    TUESDAY(Graha.MARS),
    WEDNESDAY(Graha.MERCURY),
    THURSDAY(Graha.JUPITER),
    FRIDAY(Graha.VENUS),
    SATURDAY(Graha.SATURN);

    private final Graha lord;

    KpWeekday(Graha lord) {
        this.lord = lord;
    }

    public Graha lord() {
        return lord;
    }

    public static KpWeekday of(DayOfWeek dow) {
        // DayOfWeek: MONDAY(1)..SUNDAY(7); this enum: SUNDAY(0)..SATURDAY(6)
        return values()[dow.getValue() % 7];
    }

    /** The resolved weekday plus whether the civil-day fallback was used. */
    public record Resolution(KpWeekday weekday, boolean fallback) {}

    /**
     * Resolve the KP weekday for a judgment moment.
     *
     * <p>The weekday is that of the local civil date the day-starting sunrise falls
     * in. The true civil timezone is SPEC-007; here the local date is approximated
     * as {@code instant + longitude/15 h} (mean local time) — wrong only for a
     * place with a large timezone-vs-LMT offset whose sunrise is near midnight.
     * If the Sun does not rise that day, the civil (LMT-adjusted midnight) weekday
     * is used and {@code fallback} is set.
     */
    public static Resolution resolve(
            Instant judgmentInstant, double latitude, double longitude, SunriseProvider sunrise) {
        Optional<Instant> sunriseInstant =
                sunrise.sunriseBefore(judgmentInstant, latitude, longitude);
        Instant anchor = sunriseInstant.orElse(judgmentInstant);
        long lmtOffsetSeconds = Math.round(longitude / 15.0 * 3600.0);
        LocalDate localDate =
                anchor.plusSeconds(lmtOffsetSeconds).atOffset(ZoneOffset.UTC).toLocalDate();
        return new Resolution(of(localDate.getDayOfWeek()), sunriseInstant.isEmpty());
    }
}
