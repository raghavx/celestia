package com.celestia.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.ephemeris.BirthData;
import com.celestia.ephemeris.EngineVersion;
import com.celestia.ephemeris.swisseph.SwissEphemerisConfig;
import com.celestia.ephemeris.swisseph.SwissEphemerisPositionProvider;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** T016 — no container. */
class CurrentEngineVersionTest {

    private static final SwissEphemerisConfig CONFIG = SwissEphemerisConfig.resolve();
    private static final CurrentEngineVersion VERSION =
            new CurrentEngineVersion(new SwissEphemerisPositionProvider(CONFIG), CONFIG);

    private static BirthData birthInYear(int year) {
        return new BirthData(Instant.parse(year + "-06-15T09:00:00Z"), 18.52, 73.85);
    }

    @Test
    void carriesTheCurrentRulesAndDeltaTModel() {
        EngineVersion v = VERSION.forBirth(birthInYear(1985));
        assertThat(v.rules()).isEqualTo(EngineVersion.RULES);
        assertThat(v.deltaTModel()).isEqualTo("se-builtin");
        assertThat(v.sePort()).isNotBlank();
        assertThat(v.id()).contains("/");
    }

    @Test
    void inRangeBirthIsSwiephWhenEphemerisDataIsPresent_elseMoseph() {
        String expectedTag = CONFIG.supports(1985) ? "swieph" : "moseph";
        assertThat(VERSION.forBirth(birthInYear(1985)).ephemerisData()).isEqualTo(expectedTag);
    }

    @Test
    void outOfRangeBirthIsAlwaysMoseph() {
        assertThat(VERSION.forBirth(birthInYear(1500)).ephemerisData()).isEqualTo("moseph");
    }

    @Test
    void aBirthOutsideTheRangeHasADifferentIdFromOneInside() {
        // Distinguishes the two only when .se1 data is present (otherwise both are moseph).
        if (CONFIG.hasEphemerisData()) {
            assertThat(VERSION.id(birthInYear(1985)))
                    .isNotEqualTo(VERSION.id(birthInYear(1500)));
        }
    }
}
