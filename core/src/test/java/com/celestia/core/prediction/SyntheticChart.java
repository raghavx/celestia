package com.celestia.core.prediction;

import com.celestia.core.chart.AnglePoint;
import com.celestia.core.chart.Bhavas;
import com.celestia.core.chart.Cusp;
import com.celestia.core.chart.HousePlacement;
import com.celestia.core.chart.NatalChart;
import com.celestia.core.lordage.KpLordage;
import com.celestia.core.lordage.Sign;
import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.Angle;
import com.celestia.ephemeris.Ayanamsa;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.GrahaPosition;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a deterministic {@link NatalChart} directly from graha longitudes and a
 * cusp ring — no ephemeris. For unit-testing pure chart consumers (SPEC-003+).
 */
final class SyntheticChart {

    private SyntheticChart() {}

    /** Twelve equal 30&deg; cusps starting at {@code ascLongitude}. */
    static List<Double> equalCusps(double ascLongitude) {
        List<Double> cusps = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            cusps.add(norm(ascLongitude + i * 30.0));
        }
        return cusps;
    }

    static NatalChart of(Map<Graha, Double> longitudes, List<Double> cuspLongitudes) {
        if (cuspLongitudes.size() != 12) {
            throw new IllegalArgumentException("expected 12 cusps");
        }
        List<Cusp> cusps = new ArrayList<>();
        for (int h = 1; h <= 12; h++) {
            double lon = norm(cuspLongitudes.get(h - 1));
            cusps.add(new Cusp(h, lon, KpLordage.chainFor(lon)));
        }
        List<Double> ring = cusps.stream().map(Cusp::longitude).toList();
        AnglePoint ascendant =
                new AnglePoint(Angle.ASCENDANT, cusps.get(0).longitude(), cusps.get(0).lordChain());
        AnglePoint midheaven =
                new AnglePoint(Angle.MIDHEAVEN, cusps.get(9).longitude(), cusps.get(9).lordChain());
        Sign ascSign = Sign.at(cusps.get(0).longitude());

        Map<Graha, GrahaPosition> positions = new EnumMap<>(Graha.class);
        Map<Graha, HousePlacement> placements = new EnumMap<>(Graha.class);
        for (Graha g : Graha.values()) {
            Double raw = longitudes.get(g);
            if (raw == null) {
                throw new IllegalArgumentException("missing longitude for " + g);
            }
            double lon = norm(raw);
            positions.put(g, new GrahaPosition(g, lon, 0.0, 1.0, Accuracy.FULL));
            placements.put(g, new HousePlacement(
                    g, Bhavas.bhavaOf(lon, ring), Bhavas.rasiHouseOf(Sign.at(lon), ascSign)));
        }

        return new NatalChart(
                new BirthData(Instant.parse("2000-01-01T00:00:00Z"), 0.0, 0.0),
                positions, cusps, ascendant, midheaven, placements,
                Ayanamsa.KP_NEW, Accuracy.FULL,
                new EngineVersion(EngineVersion.RULES, "test", "test", "test"));
    }

    private static double norm(double d) {
        double x = d % 360.0;
        return x < 0 ? x + 360.0 : x;
    }
}
