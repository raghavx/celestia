package com.celestia.core.horary;

import com.celestia.core.judgement.KpWeekday;
import com.celestia.core.judgement.RulingPlanets;
import com.celestia.core.judgement.RulingPlanetsFactory;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EphemerisResult;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.PositionProvider;
import com.celestia.ephemeris.SunriseProvider;

/**
 * The KP ruling planets for a horary judgment — the SPEC-003 ruling planets with
 * the lagna sign / star / sub lords taken from the <b>number's</b> Ascendant
 * (arc midpoint), and the Moon lords and the day lord from the judgment moment.
 * No change to {@link RulingPlanetsFactory}. Contract:
 * {@code specs/005-kp-horary/contracts/horary-api.md}.
 */
public final class HoraryRulingPlanets {

    private HoraryRulingPlanets() {}

    public static RulingPlanets at(
            int number, BirthData judgment, PositionProvider positions, SunriseProvider sunrise) {
        return at(number, judgment, positions, sunrise, RulingPlanets.Options.defaults());
    }

    public static RulingPlanets at(
            int number, BirthData judgment, PositionProvider positions, SunriseProvider sunrise,
            RulingPlanets.Options options) {
        double ascendantLongitude = Horary249.arc(number).midpointDeg();
        EphemerisResult ephemeris = positions.positions(judgment.instant());
        KpWeekday.Resolution weekday = KpWeekday.resolve(
                judgment.instant(), judgment.latitude(), judgment.longitude(), sunrise);

        return RulingPlanetsFactory.compute(
                judgment, ascendantLongitude,
                ephemeris.position(Graha.MOON).longitude(),
                ephemeris.position(Graha.RAHU).longitude(),
                weekday.weekday(), weekday.fallback(),
                ephemeris.position(Graha.MOON).accuracy(),
                ephemeris.engineVersion(), options);
    }
}
