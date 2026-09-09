package com.celestia.ephemeris.swisseph;

import com.celestia.ephemeris.EphemerisException;
import com.celestia.ephemeris.JulianDay;
import com.celestia.ephemeris.SunriseProvider;
import com.celestia.ephemeris.TimeScales;
import de.thmac.swisseph.DblObj;
import de.thmac.swisseph.SweConst;
import de.thmac.swisseph.SwissEph;
import java.time.Instant;
import java.util.Optional;

/**
 * {@link SunriseProvider} backed by the Swiss Ephemeris Java port
 * ({@code swe_rise_trans}). The {@code de.thmac.swisseph} types never leave this
 * class (FR-018).
 *
 * <p>Sunrise = the Sun's upper limb at the true horizon with standard refraction
 * (the port default — no {@code SE_BIT_DISC_CENTER}, no {@code SE_BIT_NO_REFRACTION}).
 * The {@link SwissEph} handle is not thread-safe, so every call holds {@code this}
 * monitor.
 */
public final class SwissEphemerisSunriseProvider implements SunriseProvider {

    /** Widest gap between successive sunrises (polar) we still step across. */
    private static final double SEARCH_BACK_DAYS = 1.05;

    private final SwissEph swe;
    private final int epheFlag;

    public SwissEphemerisSunriseProvider() {
        this(SwissEphemerisConfig.resolve());
    }

    public SwissEphemerisSunriseProvider(SwissEphemerisConfig config) {
        try {
            this.swe = new SwissEph();
            if (config.hasEphemerisData()) {
                swe.swe_set_ephe_path(config.ephePath());
            }
            swe.swe_set_sid_mode(SweConst.SE_SIDM_KRISHNAMURTI, 0, 0);
            this.epheFlag = config.hasEphemerisData() ? SweConst.SEFLG_SWIEPH : SweConst.SEFLG_MOSEPH;
        } catch (RuntimeException e) {
            throw new EphemerisException("failed to initialise Swiss Ephemeris", e);
        }
    }

    @Override
    public Optional<Instant> sunriseBefore(Instant judgmentInstant, double latitude, double longitude) {
        if (judgmentInstant == null) {
            throw new EphemerisException("judgmentInstant must not be null");
        }
        if (!(Math.abs(latitude) <= 90.0)) {
            throw new IllegalArgumentException("latitude out of [-90,90]: " + latitude);
        }
        if (!(Math.abs(longitude) <= 180.0)) {
            throw new IllegalArgumentException("longitude out of [-180,180]: " + longitude);
        }

        JulianDay jd = TimeScales.of(judgmentInstant);
        double target = jd.jdUt();
        double[] geopos = {longitude, latitude, 0.0};

        synchronized (this) {
            Double rise = nextRise(target - SEARCH_BACK_DAYS, geopos);
            if (rise == null || rise > target) {
                return Optional.empty();
            }
            // advance to the latest rise still <= target (bounded: <= 2 steps)
            for (int i = 0; i < 3; i++) {
                Double next = nextRise(rise + 1e-6, geopos);
                if (next == null || next > target) {
                    break;
                }
                rise = next;
            }
            return Optional.of(TimeScales.instantFromJulianDayUt(rise));
        }
    }

    /** JD(UT) of the first sunrise at or after {@code fromJdUt}, or {@code null} if none. */
    private Double nextRise(double fromJdUt, double[] geopos) {
        DblObj tret = new DblObj();
        StringBuffer serr = new StringBuffer();
        int rc = swe.swe_rise_trans(
                fromJdUt, SweConst.SE_SUN, null, epheFlag, SweConst.SE_CALC_RISE,
                geopos, 0.0, 0.0, tret, serr);
        return rc < 0 ? null : tret.val;
    }
}
