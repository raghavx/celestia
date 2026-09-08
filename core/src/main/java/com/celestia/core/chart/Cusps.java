package com.celestia.core.chart;

import com.celestia.core.lordage.KpLordage;
import com.celestia.ephemeris.Angle;
import com.celestia.ephemeris.HouseResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a raw {@link HouseResult} into {@link Cusp}s and {@link AnglePoint}s with
 * their full KP lord chains (via {@link KpLordage}).
 */
public final class Cusps {

    private Cusps() {}

    /** The twelve cusps, house order, each with its lord chain. */
    public static List<Cusp> fromHouseResult(HouseResult houses) {
        List<Cusp> cusps = new ArrayList<>(12);
        for (int house = 1; house <= 12; house++) {
            double longitude = houses.cusp(house);
            cusps.add(new Cusp(house, longitude, KpLordage.chainFor(longitude)));
        }
        return List.copyOf(cusps);
    }

    /** The Ascendant or Midheaven as an {@link AnglePoint}. */
    public static AnglePoint anglePoint(HouseResult houses, Angle angle) {
        double longitude = houses.angles().get(angle);
        return new AnglePoint(angle, longitude, KpLordage.chainFor(longitude));
    }
}
