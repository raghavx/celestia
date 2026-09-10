package com.celestia.geo.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlaceCandidateTest {

    @Test
    void rejectsOutOfRangeCoordinates() {
        assertThatThrownBy(() -> new PlaceCandidate("i", "n", "", "", 95.0, 10.0, "s"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlaceCandidate("i", "n", "", "", 10.0, 200.0, "s"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankIdNameOrSource() {
        assertThatThrownBy(() -> new PlaceCandidate(" ", "n", "", "", 0, 0, "s"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlaceCandidate("i", "", "", "", 0, 0, "s"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlaceCandidate("i", "n", "", "", 0, 0, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullCountryAndRegionBecomeEmptyStrings() {
        PlaceCandidate c = new PlaceCandidate("i", "n", null, null, 0, 0, "s");
        assertThat(c.country()).isEmpty();
        assertThat(c.adminRegion()).isEmpty();
    }

    @Test
    void placeQueryRejectsBlankRaw() {
        assertThatThrownBy(() -> PlaceQuery.of("   ")).isInstanceOf(IllegalArgumentException.class);
    }
}
