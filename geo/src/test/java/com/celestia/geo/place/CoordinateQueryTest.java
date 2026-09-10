package com.celestia.geo.place;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CoordinateQueryTest {

    @Test
    void parsesACommaSeparatedPair() {
        PlaceCandidate c = CoordinateQuery.parse("18.52, 73.85").orElseThrow();
        assertThat(c.latitude()).isEqualTo(18.52);
        assertThat(c.longitude()).isEqualTo(73.85);
        assertThat(c.source()).isEqualTo("direct-input");
        assertThat(c.id()).isNotBlank();
    }

    @Test
    void parsesWhitespaceSeparatedAndSignedPairs() {
        assertThat(CoordinateQuery.parse("-33.87 151.21")).isPresent();
        assertThat(CoordinateQuery.parse("+40.7,-74.0")).isPresent();
        assertThat(CoordinateQuery.parse("  0 , 0  ")).isPresent();
    }

    @Test
    void sameCoordinatesGiveTheSameStableId() {
        assertThat(CoordinateQuery.parse("18.52,73.85").orElseThrow().id())
                .isEqualTo(CoordinateQuery.parse("18.520, 73.8500").orElseThrow().id());
    }

    @Test
    void rejectsOutOfRangeAndNonCoordinates() {
        assertThat(CoordinateQuery.parse("200, 0")).isEmpty();
        assertThat(CoordinateQuery.parse("0, 200")).isEmpty();
        assertThat(CoordinateQuery.parse("not a place")).isEmpty();
        assertThat(CoordinateQuery.parse("Pune, India")).isEmpty();
        assertThat(CoordinateQuery.parse("12345")).isEmpty();
        assertThat(CoordinateQuery.parse(null)).isEmpty();
    }
}
