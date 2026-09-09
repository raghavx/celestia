package com.celestia.core.judgement;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisSunriseProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** SPEC-003 SC-005: ruling planets are reproducible (compute and at). */
class RulingPlanetsDeterminismTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    private static final BirthData JUDGMENT =
            new BirthData(Instant.parse("2026-01-01T12:00:00Z"), 28.6139, 77.209);

    @Test
    void computeIsDeterministic() {
        EngineVersion engine = new EngineVersion(EngineVersion.RULES, "x", "x", "swieph");
        RulingPlanets a = RulingPlanetsFactory.compute(
                JUDGMENT, 76.68, 50.10, 250.0, KpWeekday.THURSDAY, false, Accuracy.FULL, engine,
                RulingPlanets.Options.defaults());
        RulingPlanets b = RulingPlanetsFactory.compute(
                JUDGMENT, 76.68, 50.10, 250.0, KpWeekday.THURSDAY, false, Accuracy.FULL, engine,
                RulingPlanets.Options.defaults());
        assertThat(a).isEqualTo(b);
    }

    @Test
    @EnabledIf("hasEphemerisData")
    void atIsDeterministic() {
        var factory = new RulingPlanetsFactory(
                new SwissEphemerisPositionProvider(),
                new SwissEphemerisHouseProvider(),
                new SwissEphemerisSunriseProvider());
        assertThat(factory.at(JUDGMENT)).isEqualTo(factory.at(JUDGMENT));
    }
}
