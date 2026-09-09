package com.celestia.core.judgement;

import com.celestia.core.lordage.KpLordage;
import com.celestia.core.lordage.LordChain;
import com.celestia.core.lordage.Longitudes;
import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.Angle;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.EphemerisResult;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.HouseProvider;
import com.celestia.ephemeris.PositionProvider;
import com.celestia.ephemeris.SunriseProvider;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the KP {@link RulingPlanets} for a judgment moment (contract:
 * {@code specs/003-significators-ruling-planets/contracts/ruling-planets-api.md}).
 *
 * <p>{@link #at(BirthData)} wires the three ephemeris providers;
 * {@link #compute} is the pure core.
 */
public final class RulingPlanetsFactory {

    private final PositionProvider positions;
    private final HouseProvider houses;
    private final SunriseProvider sunrise;
    private final RulingPlanets.Options options;

    public RulingPlanetsFactory(
            PositionProvider positions, HouseProvider houses, SunriseProvider sunrise) {
        this(positions, houses, sunrise, RulingPlanets.Options.defaults());
    }

    public RulingPlanetsFactory(
            PositionProvider positions, HouseProvider houses, SunriseProvider sunrise,
            RulingPlanets.Options options) {
        this.positions = positions;
        this.houses = houses;
        this.sunrise = sunrise;
        this.options = options;
    }

    /** The ruling planets for {@code judgment}, resolving the Ascendant, Moon and day lord. */
    public RulingPlanets at(BirthData judgment) {
        double ascendant = houses.anglesOnly(judgment).get(Angle.ASCENDANT);
        EphemerisResult ephemeris = positions.positions(judgment.instant());
        double moon = ephemeris.position(Graha.MOON).longitude();
        double rahu = ephemeris.position(Graha.RAHU).longitude();
        Accuracy accuracy = ephemeris.position(Graha.MOON).accuracy();

        KpWeekday.Resolution weekday = KpWeekday.resolve(
                judgment.instant(), judgment.latitude(), judgment.longitude(), sunrise);

        return compute(judgment, ascendant, moon, rahu, weekday.weekday(), weekday.fallback(),
                accuracy, ephemeris.engineVersion(), options);
    }

    /**
     * Pure: the ruling planets from already-resolved inputs.
     *
     * @param rahuLongitude sidereal longitude of the mean north node; Ketu is
     *     {@code rahuLongitude + 180}
     */
    public static RulingPlanets compute(
            BirthData judgment, double ascendantLongitude, double moonLongitude, double rahuLongitude,
            KpWeekday weekday, boolean weekdayFallback, Accuracy accuracy, EngineVersion engineVersion,
            RulingPlanets.Options options) {

        LordChain lagna = KpLordage.chainFor(ascendantLongitude);
        LordChain moon = KpLordage.chainFor(moonLongitude);

        Map<Graha, Set<RpSource>> sources = new EnumMap<>(Graha.class);
        add(sources, lagna.signLord(), RpSource.LAGNA_SIGN);
        add(sources, lagna.starLord(), RpSource.LAGNA_STAR);
        add(sources, moon.signLord(), RpSource.MOON_SIGN);
        add(sources, moon.starLord(), RpSource.MOON_STAR);
        if (options.includeSubLords()) {
            add(sources, lagna.subLord(), RpSource.LAGNA_SUB);
            add(sources, moon.subLord(), RpSource.MOON_SUB);
        }
        add(sources, weekday.lord(), RpSource.DAY_LORD);

        // Node addition (research.md §3) — Rahu first, then Ketu (order matters:
        // a node just added can satisfy the next node's "lord is ruling" test).
        double rahu = Longitudes.normalize(rahuLongitude);
        for (Map.Entry<Graha, Double> node : List.of(
                Map.entry(Graha.RAHU, rahu),
                Map.entry(Graha.KETU, Longitudes.normalize(rahu + 180.0)))) {
            LordChain nc = KpLordage.chainFor(node.getValue());
            boolean sharesSign = nc.sign() == lagna.sign() || nc.sign() == moon.sign();
            boolean sharesStar = nc.nakshatra() == lagna.nakshatra() || nc.nakshatra() == moon.nakshatra();
            boolean lordRuling = sources.containsKey(nc.signLord()) || sources.containsKey(nc.starLord());
            if (sharesSign || sharesStar || lordRuling) {
                add(sources, node.getKey(), RpSource.NODE);
            }
        }

        List<RulingPlanet> planets = new ArrayList<>();
        for (Graha g : Graha.values()) {
            Set<RpSource> s = sources.get(g);
            if (s != null) {
                planets.add(new RulingPlanet(g, s));
            }
        }

        return new RulingPlanets(judgment, Set.copyOf(planets), weekday.lord(), weekday,
                weekdayFallback, options.includeSubLords(), accuracy, engineVersion);
    }

    private static void add(Map<Graha, Set<RpSource>> sources, Graha graha, RpSource source) {
        sources.computeIfAbsent(graha, k -> EnumSet.noneOf(RpSource.class)).add(source);
    }
}
