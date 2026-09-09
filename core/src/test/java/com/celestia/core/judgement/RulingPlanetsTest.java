package com.celestia.core.judgement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.celestia.core.lordage.KpLordage;
import com.celestia.core.lordage.LordChain;
import com.celestia.ephemeris.Accuracy;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.Graha;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisSunriseProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** SPEC-003 US4: ruling planets for a judgment moment (FR-009…FR-017). */
class RulingPlanetsTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    private static final BirthData JUDGMENT =
            new BirthData(Instant.parse("2026-01-01T12:00:00Z"), 28.6139, 77.209);
    private static final EngineVersion ENGINE =
            new EngineVersion(EngineVersion.RULES, "test", "test", "swieph");

    private static final double ASC = 5.0; // Aries -> sign lord Mars
    private static final double MOON = 50.1; // Taurus -> sign lord Venus

    private static RulingPlanets compute(double rahu, RulingPlanets.Options options) {
        return RulingPlanetsFactory.compute(
                JUDGMENT, ASC, MOON, rahu, KpWeekday.THURSDAY, false, Accuracy.FULL, ENGINE, options);
    }

    @Test
    void carriesTheSixLagnaMoonLordsAndTheDayLord() {
        LordChain lagna = KpLordage.chainFor(ASC);
        LordChain moon = KpLordage.chainFor(MOON);
        RulingPlanets rp = compute(300.0, RulingPlanets.Options.defaults());

        assertThat(rp.sourcesFor(lagna.signLord())).contains(RpSource.LAGNA_SIGN);
        assertThat(rp.sourcesFor(lagna.starLord())).contains(RpSource.LAGNA_STAR);
        assertThat(rp.sourcesFor(lagna.subLord())).contains(RpSource.LAGNA_SUB);
        assertThat(rp.sourcesFor(moon.signLord())).contains(RpSource.MOON_SIGN);
        assertThat(rp.sourcesFor(moon.starLord())).contains(RpSource.MOON_STAR);
        assertThat(rp.sourcesFor(moon.subLord())).contains(RpSource.MOON_SUB);
        assertThat(rp.sourcesFor(Graha.JUPITER)).contains(RpSource.DAY_LORD);
        assertThat(rp.dayLord()).isEqualTo(Graha.JUPITER);
        assertThat(rp.weekday()).isEqualTo(KpWeekday.THURSDAY);
    }

    @Test
    void includeSubLordsFalseDropsTheSubLordSources() {
        RulingPlanets rp = compute(300.0, new RulingPlanets.Options(false, false));
        assertThat(rp.includeSubLords()).isFalse();
        for (RulingPlanet p : rp.planets()) {
            assertThat(p.sources()).doesNotContain(RpSource.LAGNA_SUB, RpSource.MOON_SUB);
        }
        // a graha present only via a sub lord is gone
        LordChain lagna = KpLordage.chainFor(ASC);
        Graha subOnly = lagna.subLord();
        boolean subLordAlsoElsewhere = compute(300.0, RulingPlanets.Options.defaults())
                .sourcesFor(subOnly).stream()
                .anyMatch(s -> s != RpSource.LAGNA_SUB && s != RpSource.MOON_SUB);
        if (!subLordAlsoElsewhere) {
            assertThat(rp.isRuling(subOnly)).isFalse();
        }
    }

    @Test
    void nodeAddedWhenItSharesTheMoonsSign() {
        // Rahu at 55° is in Taurus, the Moon's sign.
        RulingPlanets rp = compute(55.0, RulingPlanets.Options.defaults());
        assertThat(rp.sourcesFor(Graha.RAHU)).contains(RpSource.NODE);
    }

    @Test
    void nodeAddedWhenItsOccupiedSignLordIsAlreadyRuling() {
        // Rahu at 220° is in Scorpio (sign lord Mars); Mars is the lagna sign lord.
        RulingPlanets rp = compute(220.0, RulingPlanets.Options.defaults());
        assertThat(rp.sourcesFor(Graha.RAHU)).contains(RpSource.NODE);
    }

    @Test
    void everyRulingPlanetHasANonEmptySourceSet() {
        RulingPlanets rp = compute(123.4, RulingPlanets.Options.defaults());
        for (RulingPlanet p : rp.planets()) {
            assertThat(p.sources()).isNotEmpty();
            assertThat(rp.isRuling(p.graha())).isTrue();
        }
    }

    @Test
    void isDeterministic() {
        assertThat(compute(200.0, RulingPlanets.Options.defaults()))
                .isEqualTo(compute(200.0, RulingPlanets.Options.defaults()));
    }

    @Test
    @EnabledIf("hasEphemerisData")
    void outOfEphemerisRangeJudgmentDegradesToReducedWithoutThrowing() {
        var factory = new RulingPlanetsFactory(
                new SwissEphemerisPositionProvider(),
                new SwissEphemerisHouseProvider(),
                new SwissEphemerisSunriseProvider());
        BirthData ancient = new BirthData(Instant.parse("1600-03-21T06:00:00Z"), 28.6139, 77.209);

        RulingPlanets[] out = new RulingPlanets[1];
        assertThatCode(() -> out[0] = factory.at(ancient)).doesNotThrowAnyException();
        assertThat(out[0].accuracy()).isEqualTo(Accuracy.REDUCED);
        assertThat(out[0].planets()).isNotEmpty();
    }
}
