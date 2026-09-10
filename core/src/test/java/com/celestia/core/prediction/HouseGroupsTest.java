package com.celestia.core.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** SPEC-006 US1: the house-group taxonomy. */
class HouseGroupsTest {

    @Test
    void fromKeyResolvesAndRejects() {
        assertThat(HouseGroups.fromKey("marriage")).isEqualTo(Matter.MARRIAGE);
        assertThat(HouseGroups.fromKey("  CAREER ")).isEqualTo(Matter.CAREER);
        assertThatThrownBy(() -> HouseGroups.fromKey("lottery"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HouseGroups.fromKey(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyMatterKeyRoundTrips() {
        for (Matter m : Matter.values()) {
            assertThat(HouseGroups.fromKey(m.key())).isEqualTo(m);
        }
    }

    @Test
    void allReturnsEveryMatter() {
        assertThat(HouseGroups.all()).containsExactlyInAnyOrder(Matter.values());
    }

    @Test
    void theKnownHouseGroups() {
        assertThat(Matter.MARRIAGE.favourable()).containsExactlyInAnyOrder(2, 7, 11);
        assertThat(Matter.MARRIAGE.obstructive()).contains(6);
        assertThat(Matter.CAREER.favourable()).containsExactlyInAnyOrder(2, 6, 10, 11);
    }
}
