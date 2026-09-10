package com.celestia.core.horary;

import com.celestia.core.chart.NatalChart;
import com.celestia.core.chart.NatalChartFactory;
import com.celestia.ephemeris.Angle;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EphemerisResult;
import com.celestia.ephemeris.HoraryHouseProvider;
import com.celestia.ephemeris.HouseResult;
import com.celestia.ephemeris.PositionProvider;
import com.celestia.core.chart.AnglePoint;

/**
 * Builds a KP horary chart: the Ascendant is fixed by the querent's number
 * (1–249), the planets and the other cusps are for the judgment moment and place.
 * A horary chart is an ordinary {@link NatalChart}. Contract:
 * {@code specs/005-kp-horary/contracts/horary-api.md}.
 */
public final class HoraryChartFactory {

    private final PositionProvider positions;
    private final HoraryHouseProvider houses;

    public HoraryChartFactory(PositionProvider positions, HoraryHouseProvider houses) {
        this.positions = positions;
        this.houses = houses;
    }

    /**
     * The horary Ascendant for {@code number} — the midpoint of arc <i>N</i> with
     * its KP lord chain, as an Ascendant angle. Clock-independent.
     */
    public static AnglePoint ascendant(int number) {
        HoraryArc arc = Horary249.arc(number);
        return new AnglePoint(Angle.ASCENDANT, arc.midpointDeg(), arc.lordChain());
    }

    /**
     * Cast the horary chart for {@code number} at the judgment moment / place.
     * Cusp 1 is the number's Ascendant, not the instant's. Propagates
     * {@code PlacidusUndefinedException} for a polar judgment latitude.
     */
    public NatalChart cast(int number, BirthData judgment) {
        double ascendantLongitude = Horary249.arc(number).midpointDeg();
        EphemerisResult ephemeris = positions.positions(judgment.instant());
        HouseResult horaryHouses = houses.housesFor(judgment, ascendantLongitude);
        return assemble(number, judgment, ephemeris, horaryHouses);
    }

    /**
     * Assemble a horary chart from already-computed inputs. Pure. Asserts cusp 1
     * equals the number's Ascendant, then delegates to
     * {@link NatalChartFactory#assemble}.
     */
    public static NatalChart assemble(
            int number, BirthData judgment, EphemerisResult positions, HouseResult horaryHouses) {
        double ascendantLongitude = Horary249.arc(number).midpointDeg();
        if (Double.compare(horaryHouses.cusp(1), ascendantLongitude) != 0) {
            throw new IllegalArgumentException(
                    "horary houses cusp 1 " + horaryHouses.cusp(1) + " != arc " + number
                            + " Ascendant " + ascendantLongitude);
        }
        return NatalChartFactory.assemble(judgment, positions, horaryHouses);
    }
}
