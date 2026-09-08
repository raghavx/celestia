package com.celestia.core.chart;

import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.Ayanamsa;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.GrahaPosition;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * The immutable natal chart — SPEC-001 positions plus SPEC-002 cusps, angles and
 * house placements — consumed by SPEC-003 (significators), SPEC-004 (dasha) and
 * SPEC-005 (horary).
 */
public record NatalChart(
        BirthData birthData,
        Map<Graha, GrahaPosition> positions,
        List<Cusp> cusps,
        AnglePoint ascendant,
        AnglePoint midheaven,
        Map<Graha, HousePlacement> placements,
        Ayanamsa ayanamsa,
        Accuracy accuracy,
        EngineVersion engineVersion) {

    public NatalChart {
        if (cusps.size() != 12) {
            throw new IllegalArgumentException("expected 12 cusps, got " + cusps.size());
        }
        for (int i = 0; i < 12; i++) {
            if (cusps.get(i).house() != i + 1) {
                throw new IllegalArgumentException("cusps must be in house order");
            }
        }
        if (Double.compare(cusps.get(0).longitude(), ascendant.longitude()) != 0) {
            throw new IllegalArgumentException("cusp 1 must equal the Ascendant longitude");
        }
        if (!positions.keySet().equals(EnumSet.allOf(Graha.class))
                || !placements.keySet().equals(EnumSet.allOf(Graha.class))) {
            throw new IllegalArgumentException("positions and placements must cover the nine grahas");
        }
        cusps = List.copyOf(cusps);
        positions = Collections.unmodifiableMap(new EnumMap<>(positions));
        placements = Collections.unmodifiableMap(new EnumMap<>(placements));
    }

    public GrahaPosition position(Graha graha) {
        return positions.get(graha);
    }

    /** Cusp of house {@code h} (1..12). */
    public Cusp cusp(int house) {
        return cusps.get(house - 1);
    }

    /** The cuspal sub lord of house {@code h} — the KP determinant of its matters. */
    public Graha cuspSubLord(int house) {
        return cusp(house).subLord();
    }

    public HousePlacement placement(Graha graha) {
        return placements.get(graha);
    }
}
