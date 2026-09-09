package com.celestia.core.judgement;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.golden.GoldenChart;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisHouseProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisSunriseProvider;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * SPEC-003 SC-003: the worked ruling-planet example (obama-1961's
 * {@code expected.ruling_planets}) reproduces exactly — planet set, sources,
 * weekday, day lord, and the sunrise instant.
 */
@EnabledIf("hasEphemerisData")
class RulingPlanetsGoldenTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @Test
    void reproducesTheGoldenRulingPlanetExample() {
        GoldenChart.RulingPlanetsRef ref = null;
        for (GoldenChart chart : GoldenChart.loadAll()) {
            if (chart.rulingPlanets() != null) {
                ref = chart.rulingPlanets();
            }
        }
        assertThat(ref).as("a golden chart carries a ruling_planets block").isNotNull();

        BirthData judgment =
                new BirthData(ref.judgmentInstant(), ref.latitude(), ref.longitude());

        var factory = new RulingPlanetsFactory(
                new SwissEphemerisPositionProvider(),
                new SwissEphemerisHouseProvider(),
                new SwissEphemerisSunriseProvider());
        RulingPlanets rp = factory.at(judgment);

        assertThat(rp.weekday().name()).isEqualTo(ref.weekday());
        assertThat(rp.dayLord().name()).isEqualTo(ref.dayLord());
        assertThat(rp.dayLordFallback()).isEqualTo(ref.dayLordFallback());
        assertThat(rp.includeSubLords()).isEqualTo(ref.includeSubLords());

        Map<String, Set<String>> actual = new HashMap<>();
        for (RulingPlanet p : rp.planets()) {
            actual.put(p.graha().name(),
                    p.sources().stream().map(Enum::name).collect(Collectors.toSet()));
        }
        assertThat(actual).isEqualTo(ref.planetSources());

        // sunrise instant to the second (KP boundary); allow a 2 s port/wrapper delta
        var sunrise = new SwissEphemerisSunriseProvider()
                .sunriseBefore(ref.judgmentInstant(), ref.latitude(), ref.longitude());
        assertThat(sunrise).isPresent();
        assertThat(Duration.between(sunrise.get(), ref.sunriseUtc()).abs())
                .isLessThanOrEqualTo(Duration.ofSeconds(2));
    }
}
