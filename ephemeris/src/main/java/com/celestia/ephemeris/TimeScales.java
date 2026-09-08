package com.celestia.ephemeris;

import de.thmac.swisseph.SweDate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Converts a UTC {@link Instant} to the {@link JulianDay} the ephemeris needs
 * (FR-001). &Delta;T comes from the Swiss Ephemeris port's built-in model
 * (Espenak-Meeus); its tag is recorded in {@link EngineVersion}.
 *
 * <p>Pure: no wall clock, no I/O.
 */
public final class TimeScales {

    /** Tag for the &Delta;T model, for {@link EngineVersion#deltaTModel()}. */
    public static final String DELTA_T_MODEL = "se-builtin";

    private static final double SECONDS_PER_DAY = 86_400.0;

    private TimeScales() {}

    public static JulianDay of(Instant utcInstant) {
        if (utcInstant == null) {
            throw new EphemerisException("instant must not be null");
        }
        OffsetDateTime t = utcInstant.atOffset(ZoneOffset.UTC);
        double hour = t.getHour()
                + t.getMinute() / 60.0
                + (t.getSecond() + t.getNano() / 1_000_000_000.0) / 3600.0;

        // proleptic Gregorian (SE_GREG_CAL) — matches java.time; our range is post-1582
        double jdUt = SweDate.getJulDay(t.getYear(), t.getMonthValue(), t.getDayOfMonth(), hour, true);
        double deltaTDays = SweDate.getDeltaT(jdUt);
        return new JulianDay(jdUt, jdUt + deltaTDays, deltaTDays * SECONDS_PER_DAY);
    }
}
