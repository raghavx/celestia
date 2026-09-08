package com.celestia.core.chart;

import com.celestia.core.lordage.Sign;
import com.celestia.ephemeris.Angle;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EphemerisResult;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.HouseProvider;
import com.celestia.ephemeris.HouseResult;
import com.celestia.ephemeris.PositionProvider;
import java.util.EnumMap;
import java.util.Map;

/**
 * Builds a {@link NatalChart}.
 *
 * <p>{@link #assemble} is pure (no I/O) — the unit-testable core. The instance
 * {@link #cast} wires a {@link PositionProvider} and a {@link HouseProvider}.
 */
public final class NatalChartFactory {

    private final PositionProvider positionProvider;
    private final HouseProvider houseProvider;

    public NatalChartFactory(PositionProvider positionProvider, HouseProvider houseProvider) {
        this.positionProvider = positionProvider;
        this.houseProvider = houseProvider;
    }

    /** Cast the chart; propagates {@code PlacidusUndefinedException} for polar births. */
    public NatalChart cast(BirthData birthData) {
        EphemerisResult positions = positionProvider.positions(birthData.instant());
        HouseResult houses = houseProvider.houses(birthData);
        return assemble(birthData, positions, houses);
    }

    /**
     * Assemble a chart from already-computed inputs (both must be for the same
     * {@code birthData}).
     */
    public static NatalChart assemble(BirthData birthData, EphemerisResult positions, HouseResult houses) {
        var cusps = Cusps.fromHouseResult(houses);
        AnglePoint ascendant = Cusps.anglePoint(houses, Angle.ASCENDANT);
        AnglePoint midheaven = Cusps.anglePoint(houses, Angle.MIDHEAVEN);
        Sign ascendantSign = ascendant.lordChain().sign();

        Map<Graha, HousePlacement> placements = new EnumMap<>(Graha.class);
        for (Graha g : Graha.values()) {
            double longitude = positions.position(g).longitude();
            int bhava = Bhavas.bhavaOf(longitude, houses.cuspLongitudes());
            int rasiHouse = Bhavas.rasiHouseOf(Sign.at(longitude), ascendantSign);
            placements.put(g, new HousePlacement(g, bhava, rasiHouse));
        }

        return new NatalChart(
                birthData,
                positions.positions(),
                cusps,
                ascendant,
                midheaven,
                placements,
                positions.ayanamsa(),
                positions.position(Graha.SUN).accuracy(),
                positions.engineVersion());
    }
}
