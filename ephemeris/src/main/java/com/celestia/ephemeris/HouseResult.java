package com.celestia.ephemeris;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The house cusps and angles for one birth.
 *
 * @param birthData echoed input
 * @param cuspLongitudes sidereal longitudes of houses 1..12 (index 0 = house 1),
 *     an immutable {@code List<Double>} (not a {@code double[]}, so the record has
 *     value equality). {@code get(0)} is bit-identical to
 *     {@code angles.get(Angle.ASCENDANT)}.
 * @param angles Ascendant and Midheaven longitudes, immutable
 * @param houseSystem always {@link HouseSystem#PLACIDUS}
 * @param accuracy mirrors the positions for the same instant (cusps are analytic
 *     and do not themselves degrade, but a chart carries one accuracy flag)
 * @param engineVersion rules + data identity
 */
public record HouseResult(
        BirthData birthData,
        List<Double> cuspLongitudes,
        Map<Angle, Double> angles,
        HouseSystem houseSystem,
        Accuracy accuracy,
        EngineVersion engineVersion) {

    public HouseResult {
        if (cuspLongitudes.size() != 12) {
            throw new IllegalArgumentException("expected 12 cusps, got " + cuspLongitudes.size());
        }
        cuspLongitudes = List.copyOf(cuspLongitudes);
        for (double c : cuspLongitudes) {
            if (!(c >= 0.0 && c < 360.0)) {
                throw new IllegalArgumentException("cusp longitude out of [0,360): " + c);
            }
        }
        for (int n = 0; n < 12; n++) {
            double arc = arcForward(cuspLongitudes.get(n), cuspLongitudes.get((n + 1) % 12));
            if (!(arc > 0.0)) {
                throw new IllegalArgumentException("cusp ring not monotone at house " + (n + 1));
            }
        }
        angles = Collections.unmodifiableMap(new EnumMap<>(angles));
        if (!angles.containsKey(Angle.ASCENDANT) || !angles.containsKey(Angle.MIDHEAVEN)) {
            throw new IllegalArgumentException("angles must contain ASCENDANT and MIDHEAVEN");
        }
        if (Double.compare(cuspLongitudes.get(0), angles.get(Angle.ASCENDANT)) != 0) {
            throw new IllegalArgumentException("cusp 1 must equal the Ascendant");
        }
    }

    /** Longitude of house {@code n} (1..12). */
    public double cusp(int house) {
        return cuspLongitudes.get(house - 1);
    }

    private static double arcForward(double from, double to) {
        double d = (to - from) % 360.0;
        return d < 0 ? d + 360.0 : d;
    }
}
