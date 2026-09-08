package com.celestia.ephemeris.swisseph;

import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.Ayanamsa;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.EphemerisException;
import com.celestia.ephemeris.EphemerisResult;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.GrahaPosition;
import com.celestia.ephemeris.JulianDay;
import com.celestia.ephemeris.PositionProvider;
import com.celestia.ephemeris.TimeScales;
import de.thmac.swisseph.SweConst;
import de.thmac.swisseph.SwissEph;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;

/**
 * {@link PositionProvider} backed by the Swiss Ephemeris Java port. The
 * {@code de.thmac.swisseph} types never leave this class (FR-018).
 *
 * <p>The underlying {@link SwissEph} handle is not thread-safe, so every
 * computation holds {@code this} monitor; {@link #positions(Instant)} is safe to
 * call concurrently.
 */
public final class SwissEphemerisPositionProvider implements PositionProvider {

    private static final int SIDEREAL_SPEED = SweConst.SEFLG_SIDEREAL | SweConst.SEFLG_SPEED;

    /** SUN..SATURN plus the mean node (Rahu), in a fixed order. Ketu is derived. */
    private static final Map<Graha, Integer> SWE_ID = new EnumMap<>(Graha.class);

    static {
        SWE_ID.put(Graha.SUN, SweConst.SE_SUN);
        SWE_ID.put(Graha.MOON, SweConst.SE_MOON);
        SWE_ID.put(Graha.MARS, SweConst.SE_MARS);
        SWE_ID.put(Graha.MERCURY, SweConst.SE_MERCURY);
        SWE_ID.put(Graha.JUPITER, SweConst.SE_JUPITER);
        SWE_ID.put(Graha.VENUS, SweConst.SE_VENUS);
        SWE_ID.put(Graha.SATURN, SweConst.SE_SATURN);
        SWE_ID.put(Graha.RAHU, SweConst.SE_MEAN_NODE);
    }

    private final SwissEphemerisConfig config;
    private final SwissEph swe;
    private final String sePortVersion;

    public SwissEphemerisPositionProvider() {
        this(SwissEphemerisConfig.resolve());
    }

    public SwissEphemerisPositionProvider(SwissEphemerisConfig config) {
        this.config = config;
        try {
            this.swe = new SwissEph();
            if (config.hasEphemerisData()) {
                swe.swe_set_ephe_path(config.ephePath());
            }
            swe.swe_set_sid_mode(SweConst.SE_SIDM_KRISHNAMURTI, 0, 0);
            this.sePortVersion = swe.swe_version();
        } catch (RuntimeException e) {
            throw new EphemerisException("failed to initialise Swiss Ephemeris", e);
        }
    }

    @Override
    public EphemerisResult positions(Instant utcInstant) {
        if (utcInstant == null) {
            throw new EphemerisException("instant must not be null");
        }
        JulianDay jd = TimeScales.of(utcInstant);
        int year = utcInstant.atOffset(ZoneOffset.UTC).getYear();
        boolean requestFull = config.supports(year);
        int flags = SIDEREAL_SPEED | (requestFull ? SweConst.SEFLG_SWIEPH : SweConst.SEFLG_MOSEPH);

        Map<Graha, double[]> raw = new EnumMap<>(Graha.class);
        boolean moshierFallback = false;
        synchronized (this) {
            for (Map.Entry<Graha, Integer> e : SWE_ID.entrySet()) {
                double[] xx = new double[6];
                StringBuffer serr = new StringBuffer();
                int rc = swe.swe_calc_ut(jd.jdUt(), e.getValue(), flags, xx, serr);
                if (rc < 0) {
                    throw new EphemerisException(
                            "swe_calc_ut failed for " + e.getKey() + ": " + serr);
                }
                if ((rc & SweConst.SEFLG_MOSEPH) != 0) {
                    moshierFallback = true;
                }
                raw.put(e.getKey(), xx);
            }
        }

        Accuracy accuracy = (requestFull && !moshierFallback) ? Accuracy.FULL : Accuracy.REDUCED;

        Map<Graha, GrahaPosition> positions = new EnumMap<>(Graha.class);
        for (Map.Entry<Graha, double[]> e : raw.entrySet()) {
            double[] xx = e.getValue();
            positions.put(e.getKey(), position(e.getKey(), xx[0], xx[1], xx[3], accuracy));
        }
        double[] rahu = raw.get(Graha.RAHU);
        positions.put(Graha.KETU, position(Graha.KETU, rahu[0] + 180.0, -rahu[1], rahu[3], accuracy));

        EngineVersion version = new EngineVersion(
                EngineVersion.RULES, sePortVersion, TimeScales.DELTA_T_MODEL,
                accuracy == Accuracy.FULL ? "swieph" : "moseph");

        return new EphemerisResult(utcInstant, jd, Ayanamsa.KP_NEW, positions, version);
    }

    private static GrahaPosition position(Graha graha, double lonDeg, double latDeg, double speed, Accuracy acc) {
        double lon = lonDeg % 360.0;
        if (lon < 0.0) {
            lon += 360.0;
        }
        if (lon == 360.0) {
            lon = 0.0;
        }
        return new GrahaPosition(graha, lon, latDeg, speed, acc);
    }
}
