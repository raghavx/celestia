package com.celestia.geo.place;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FixtureGeocoderTest {

    private final Geocoder geocoder = FixtureGeocoderLoader.geocoder();

    @Test
    void aKnownQueryReturnsItsCandidates() {
        GeocodeResult r = geocoder.geocode(PlaceQuery.of("Pune"));
        assertThat(r.candidates()).singleElement()
                .satisfies(c -> {
                    assertThat(c.displayName()).isEqualTo("Pune");
                    assertThat(c.country()).isEqualTo("India");
                    assertThat(c.latitude()).isEqualTo(18.5204);
                });
    }

    @Test
    void anAliasedQueryResolvesThroughNormalisation() {
        // "Bombay" -> normalised "mumbai" -> the fixture keyed "mumbai"
        assertThat(geocoder.geocode(PlaceQuery.of("Bombay")).candidates())
                .singleElement()
                .satisfies(c -> assertThat(c.displayName()).isEqualTo("Mumbai"));
    }

    @Test
    void anAmbiguousQueryReturnsEveryMatch_engineDoesNotPickOne() {
        assertThat(geocoder.geocode(PlaceQuery.of("Springfield")).candidates()).hasSize(3);
    }

    @Test
    void anUnknownQueryReturnsAnEmptyListNotAnError() {
        GeocodeResult r = geocoder.geocode(PlaceQuery.of("Xanadu-nowhere-9999"));
        assertThat(r.candidates()).isEmpty();
        assertThat(r.isEmpty()).isTrue();
    }

    @Test
    void anExactNameMatchIsRankedFirst() {
        // query "london" -> "London" (exact) before "London, Ontario"
        assertThat(geocoder.geocode(PlaceQuery.of("London")).candidates())
                .first()
                .satisfies(c -> assertThat(c.displayName()).isEqualTo("London"));
    }
}
