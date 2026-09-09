package com.celestia.ephemeris.swisseph;

import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.Angle;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.EphemerisException;
import com.celestia.ephemeris.HoraryHouseProvider;
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
 * {@link HoraryHouseProvider} backed by the Swiss Ephemeris Java port. Given the
 * number-fixed Ascendant, it finds the right ascension of the MC that produces it
 * (a closed-form inversion of the Ascendant equation — Meeus, <i>Astronomical
 * Algorithms</i> 2nd ed. ch. 13), computes the Placidus cusps from that ARMC via
 * {@code swe_houses_armc}, and converts sidereal &harr; tropical with the
 * instant's ayanamsa. The {@code de.thmac.swisseph} types never leave this class.
 *
 * <p>The {@link SwissEph} handle is not thread-safe, so every call holds
 * {@code this} monitor.
 */
public final class SwissEphemerisHoraryHouseProvider implements HoraryHouseProvider {

    private static final int PLACIDUS = 'P';

    private final SwissEphemerisConfig config;
    private final SwissEph swe;
    private final String sePortVersion;

    public SwissEphemerisHoraryHouseProvider() {
        this(SwissEphemerisConfig.resolve());
    }

    public SwissEphemerisHoraryHouseProvider(SwissEphemerisConfig config) {
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
    public HouseResult housesFor(BirthData judgment, double ascendantLongitude) {
        if (judgment == null) {
            throw new EphemerisException("judgment must not be null");
        }
        double latitude = judgment.latitude();
        if (Math.abs(latitude) >= config.polarLimit()) {
            throw new PlacidusUndefinedException(latitude, config.polarLimit());
        }
        double ascendant = norm(ascendantLongitude);

        JulianDay jd = TimeScales.of(judgment.instant());
        int year = judgment.instant().atOffset(ZoneOffset.UTC).getYear();
        Accuracy accuracy = config.supports(year) ? Accuracy.FULL : Accuracy.REDUCED;

        double[] cusp = new double[13];
        double[] ascmc = new double[10];
        List<Double> siderealCusps = new ArrayList<>(12);
        double midheaven;
        synchronized (this) {
            double ayanamsa = swe.swe_get_ayanamsa_ut(jd.jdUt());

            double[] xx = new double[6];
            StringBuffer serr = new StringBuffer();
            int rc = swe.swe_calc_ut(jd.jdUt(), SweConst.SE_ECL_NUT, SweConst.SEFLG_SWIEPH, xx, serr);
            if (rc < 0) {
                throw new EphemerisException("obliquity computation failed: " + serr);
            }
            double obliquity = xx[0];

            double tropicalAscendant = norm(ascendant + ayanamsa);
            double armc = ramcFromAscendant(tropicalAscendant, latitude, obliquity);

            swe.swe_houses_armc(armc, latitude, obliquity, PLACIDUS, cusp, ascmc);
            for (int h = 1; h <= 12; h++) {
                siderealCusps.add(norm(cusp[h] - ayanamsa));
            }
            midheaven = norm(ascmc[1] - ayanamsa);
        }
        // cusp 1 := the sidereal Ascendant, bit-identical (HouseResult requires it)
        siderealCusps.set(0, ascendant);

        Map<Angle, Double> angles = new EnumMap<>(Angle.class);
        angles.put(Angle.ASCENDANT, ascendant);
        angles.put(Angle.MIDHEAVEN, midheaven);

        return new HouseResult(
                judgment, siderealCusps, angles, HouseSystem.PLACIDUS, accuracy,
                new EngineVersion(
                        EngineVersion.RULES, sePortVersion, TimeScales.DELTA_T_MODEL,
                        accuracy == Accuracy.FULL ? "swieph" : "moseph"));
    }

    /**
     * The right ascension of the MC that yields tropical Ascendant
     * {@code lambdaDeg} at geographic latitude {@code latDeg} for obliquity
     * {@code epsDeg}. Closed-form inversion of the Ascendant equation.
     *
     * <p>Within the Placidus polar limit the discriminant is always positive
     * (|sin&epsilon;&middot;tan&phi;| &lt; cos&epsilon;), so no iterative fallback
     * is needed.
     */
    static double ramcFromAscendant(double lambdaDeg, double latDeg, double epsDeg) {
        double lambda = Math.toRadians(lambdaDeg);
        double phi = Math.toRadians(latDeg);
        double eps = Math.toRadians(epsDeg);

        double cosLambda = Math.cos(lambda);
        double sinLambda = Math.sin(lambda);
        double cosEps = Math.cos(eps);
        double k = Math.sin(eps) * Math.tan(phi);

        double a = cosLambda * cosLambda + cosEps * cosEps * sinLambda * sinLambda;
        double b = 2.0 * k * cosLambda;
        double c = k * k - cosEps * cosEps;
        double discriminant = b * b - 4.0 * a * c;
        if (discriminant < 0.0) {
            throw new PlacidusUndefinedException(latDeg, 0.0);
        }
        double rho = (-b + Math.sqrt(discriminant)) / (2.0 * a); // positive root -> Ascendant
        double cosArmc = rho * sinLambda;
        double sinArmc = (-rho * cosLambda - k) / cosEps;
        return norm(Math.toDegrees(Math.atan2(sinArmc, cosArmc)));
    }

    private static double norm(double deg) {
        double d = deg % 360.0;
        if (d < 0.0) {
            d += 360.0;
        }
        return d == 360.0 ? 0.0 : d;
    }
}
