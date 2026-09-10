package com.celestia.geo.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CachingGeocoderTest {

    /** A delegate that counts calls and returns one fixed candidate. */
    private static final class CountingGeocoder implements Geocoder {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public GeocodeResult geocode(PlaceQuery query) {
            calls.incrementAndGet();
            return new GeocodeResult(
                    query,
                    List.of(new PlaceCandidate(
                            "x", "Somewhere", "Country", "Region", 1.0, 2.0, "counting")));
        }
    }

    @Test
    void aMissDelegatesAndStores_aHitDoesNot() {
        CountingGeocoder delegate = new CountingGeocoder();
        InMemoryGeocodeCache cache = new InMemoryGeocodeCache();
        CachingGeocoder geocoder = new CachingGeocoder(delegate, cache);

        PlaceQuery q = PlaceQuery.of("  Bombay ");
        GeocodeResult first = geocoder.geocode(q);
        GeocodeResult second = geocoder.geocode(PlaceQuery.of("bombay")); // same normalised key

        assertThat(delegate.calls).hasValue(1);
        assertThat(second).isEqualTo(first);
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void aCoordinateQueryIsAnsweredDirectly_noDelegate_noCacheWrite() {
        CountingGeocoder delegate = new CountingGeocoder();
        InMemoryGeocodeCache cache = new InMemoryGeocodeCache();
        CachingGeocoder geocoder = new CachingGeocoder(delegate, cache);

        GeocodeResult r = geocoder.geocode(PlaceQuery.of("18.52, 73.85"));

        assertThat(delegate.calls).hasValue(0);
        assertThat(cache.size()).isZero();
        assertThat(r.candidates()).singleElement()
                .satisfies(c -> {
                    assertThat(c.source()).isEqualTo("direct-input");
                    assertThat(c.latitude()).isEqualTo(18.52);
                });
    }

    @Test
    void repeatedCoordinateQueriesStayDeterministic() {
        CachingGeocoder geocoder =
                new CachingGeocoder(new CountingGeocoder(), new InMemoryGeocodeCache());
        assertThat(geocoder.geocode(PlaceQuery.of("1.0, 2.0")))
                .isEqualTo(geocoder.geocode(PlaceQuery.of("1.0, 2.0")));
    }
}
