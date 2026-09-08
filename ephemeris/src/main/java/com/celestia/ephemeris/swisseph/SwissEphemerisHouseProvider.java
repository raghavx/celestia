package com.celestia.ephemeris.swisseph;

import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.Angle;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.EphemerisException;
import com.celestia.ephemeris.HouseProvider;
import com.celestia.ephemeris.HouseResult;
import com.celestia.ephemeris.HouseSystem;
import com.celestia.ephemeris.JulianDay;
import com.celestia.ephemeris.PlacidusUndefinedException;
import com.celestia.ephemeris.TimeScales;
import de.thmac.swisseph.SweConst;
import de.thmac.swisseph.SwissEph;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * {@link HouseProvider} backed by the Swiss Ephemeris Java port. The
 * {@code de.thmac.swisseph} types never leave this class (FR-014).
 *
 * <p>The {@link SwissEph} handle is not thread-safe, so every computation holds
 * {@code this} monitor.
 */
public final class SwissEphemerisHouseProvider implements HouseProvider {

    private static final int PLACIDUS = 'P';

    private final SwissEphemerisConfig config;
    private final SwissEph swe;
    private final String sePortVersion;

    public SwissEphemerisHouseProvider() {
        this(SwissEphemerisConfig.resolve());
    }

    public SwissEphemerisHouseProvider(SwissEphemerisConfig config) {
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
    public HouseResult houses(BirthData birthData) {
        requireNonNull(birthData);
        double lat = birthData.latitude();
        if (Math.abs(lat) >= config.polarLimit()) {
            throw new PlacidusUndefinedException(lat, config.polarLimit());
        }

        JulianDay jd = TimeScales.of(birthData.instant());
        int year = birthData.instant().atOffset(ZoneOffset.UTC).getYear();
        Accuracy accuracy = config.supports(year) ? Accuracy.FULL : Accuracy.REDUCED;

        double[] cusp = new double[13];
        double[] ascmc = new double[10];
        synchronized (this) {
            int rc = swe.swe_houses(
                    jd.jdUt(), SweConst.SEFLG_SIDEREAL, lat, birthData.longitude(), PLACIDUS, cusp, ascmc);
            if (rc < 0) {
                throw new EphemerisException("swe_houses failed for " + birthData + " (rc=" + rc + ")");
            }
        }

        List<Double> cusps = new ArrayList<>(12);
        cusps.add(norm(ascmc[0])); // cusp 1 := Ascendant, bit-identical
        for (int h = 2; h <= 12; h++) {
            cusps.add(norm(cusp[h]));
        }

        Map<Angle, Double> angles = new EnumMap<>(Angle.class);
        angles.put(Angle.ASCENDANT, norm(ascmc[0]));
        angles.put(Angle.MIDHEAVEN, norm(ascmc[1]));

        return new HouseResult(
                birthData, cusps, angles, HouseSystem.PLACIDUS, accuracy, engineVersion(accuracy));
    }

    @Override
    public Map<Angle, Double> anglesOnly(BirthData birthData) {
        requireNonNull(birthData);
        JulianDay jd = TimeScales.of(birthData.instant());
        double[] cusp = new double[13];
        double[] ascmc = new double[10];
        synchronized (this) {
            // The Ascendant/MC are filled even when Placidus itself fails at high
            // latitude (the port returns a negative rc but populates ascmc).
            swe.swe_houses(
                    jd.jdUt(), SweConst.SEFLG_SIDEREAL, birthData.latitude(), birthData.longitude(),
                    PLACIDUS, cusp, ascmc);
        }
        Map<Angle, Double> angles = new EnumMap<>(Angle.class);
        angles.put(Angle.ASCENDANT, norm(ascmc[0]));
        angles.put(Angle.MIDHEAVEN, norm(ascmc[1]));
        return angles;
    }

    private EngineVersion engineVersion(Accuracy accuracy) {
        return new EngineVersion(
                EngineVersion.RULES, sePortVersion, TimeScales.DELTA_T_MODEL,
                accuracy == Accuracy.FULL ? "swieph" : "moseph");
    }

    private static void requireNonNull(BirthData birthData) {
        if (birthData == null) {
            throw new EphemerisException("birthData must not be null");
        }
    }

    private static double norm(double deg) {
        double d = deg % 360.0;
        if (d < 0.0) {
            d += 360.0;
        }
        return d == 360.0 ? 0.0 : d;
    }
}
