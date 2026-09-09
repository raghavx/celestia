package com.celestia.core.horary;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.golden.GoldenChart;
import com.celestia.core.judgement.RulingPlanet;
import com.celestia.core.judgement.RulingPlanets;
import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import com.celestia.ephemeris.swisseph.SwissEphemerisSunriseProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/** SPEC-005: the worked horary case's ruling planets reproduce. */
@EnabledIf("hasEphemerisData")
class HoraryRulingPlanetsGoldenTest {

    static boolean hasEphemerisData() {
        return SwissEphemerisConfig.resolve().hasEphemerisData();
    }

    @Test
    void reproducesTheGoldenHoraryRulingPlanets() {
        GoldenChart.HoraryRef ref = null;
        for (GoldenChart chart : GoldenChart.loadAll()) {
            if (chart.horary() != null) {
                ref = chart.horary();
            }
        }
        assertThat(ref).isNotNull();

        BirthData judgment = new BirthData(ref.judgmentInstant(), ref.latitude(), ref.longitude());
        RulingPlanets rp = HoraryRulingPlanets.at(
                ref.number(), judgment, new SwissEphemerisPositionProvider(),
                new SwissEphemerisSunriseProvider());

        Map<String, Set<String>> actual = new HashMap<>();
        for (RulingPlanet p : rp.planets()) {
            actual.put(p.graha().name(),
                    p.sources().stream().map(Enum::name).collect(Collectors.toSet()));
        }
        assertThat(actual).isEqualTo(ref.rulingPlanetSources());
    }
}
