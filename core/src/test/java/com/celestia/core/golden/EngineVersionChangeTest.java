package com.celestia.core.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.ephemeris.EngineVersion;
import org.junit.jupiter.api.Test;

/** FR-015: every component of the engine version is visible in {@code id()}. */
class EngineVersionChangeTest {

    @Test
    void changingAnyComponentChangesTheId() {
        EngineVersion base = new EngineVersion("kp-1", "2.01.00", "se-builtin", "swieph");
        String id = base.id();

        assertThat(new EngineVersion("kp-2", "2.01.00", "se-builtin", "swieph").id()).isNotEqualTo(id);
        assertThat(new EngineVersion("kp-1", "2.10.03", "se-builtin", "swieph").id()).isNotEqualTo(id);
        assertThat(new EngineVersion("kp-1", "2.01.00", "iers-2020", "swieph").id()).isNotEqualTo(id);
        assertThat(new EngineVersion("kp-1", "2.01.00", "se-builtin", "moseph").id()).isNotEqualTo(id);
    }

    @Test
    void idContainsAllFourComponents() {
        String id = new EngineVersion("kp-1", "2.01.00", "se-builtin", "swieph").id();
        assertThat(id).contains("kp-1", "2.01.00", "se-builtin", "swieph");
    }
}
