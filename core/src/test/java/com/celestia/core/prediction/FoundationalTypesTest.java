package com.celestia.core.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.ephemeris.Graha;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FoundationalTypesTest {

    @Test
    void everyMatterHasADisjointNonEmptyHouseGroupInRange() {
        for (Matter m : Matter.values()) {
            assertThat(m.favourable()).as("%s favourable", m).isNotEmpty();
            assertThat(Collections.disjoint(m.favourable(), m.obstructive()))
                    .as("%s favourable/obstructive disjoint", m).isTrue();
            for (int h : m.favourable()) {
                assertThat(h).isBetween(1, 12);
            }
            for (int h : m.obstructive()) {
                assertThat(h).isBetween(1, 12);
            }
            assertThat(m.source()).isNotBlank();
            assertThat(m.key()).isEqualTo(m.name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    @Test
    void knownHouseGroups() {
        assertThat(Matter.MARRIAGE.favourable()).containsExactlyInAnyOrder(2, 7, 11);
        assertThat(Matter.CAREER.favourable()).containsExactlyInAnyOrder(2, 6, 10, 11);
        assertThat(Matter.HEALTH_RECOVERY.favourable()).containsExactlyInAnyOrder(5, 11);
    }

    @Test
    void activatedHouseValidation() {
        assertThat(new ActivatedHouse(7, 2, Set.of(Graha.VENUS, Graha.JUPITER)).strength())
                .isEqualTo(2);
        assertThatThrownBy(() -> new ActivatedHouse(7, 3, Set.of(Graha.VENUS)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActivatedHouse(13, 1, Set.of(Graha.VENUS)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActivatedHouse(1, 0, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
