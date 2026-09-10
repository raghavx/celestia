package com.celestia.geo;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.geo.place.CachingGeocoder;
import com.celestia.geo.place.CandidateRanking;
import com.celestia.geo.place.FixtureGeocoderLoader;
import com.celestia.geo.place.Geocoder;
import com.celestia.geo.place.InMemoryGeocodeCache;
import com.celestia.geo.place.PlaceQuery;
import com.celestia.geo.place.QueryNormalizer;
import com.celestia.geo.time.TimeshapeTimeZoneResolver;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** User Story 5 — equal inputs produce equal output. */
class GeoDeterminismTest {

    @Test
    void resolveIsDeterministic() {
        BirthMomentResolver resolver = new BirthMomentResolver(new TimeshapeTimeZoneResolver());
        var place = new com.celestia.geo.place.PlaceCandidate(
                "id", "Pune", "India", "MH", 18.5204, 73.8567, "fixture");

        ResolvedBirth a = resolver.resolve(place, LocalDate.of(1985, 6, 15), LocalTime.of(14, 30), null);
        ResolvedBirth b = resolver.resolve(place, LocalDate.of(1985, 6, 15), LocalTime.of(14, 30), null);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void normaliseAndRankAreIdempotent() {
        String n = QueryNormalizer.normalize("  Bandra, BOMBAY  ");
        assertThat(QueryNormalizer.normalize(n)).isEqualTo(n);

        PlaceQuery q = PlaceQuery.of("london");
        Geocoder g = FixtureGeocoderLoader.geocoder();
        var once = CandidateRanking.rank(q, g.geocode(q).candidates());
        assertThat(CandidateRanking.rank(q, once)).isEqualTo(once);
    }

    @Test
    void cachingGeocoderReturnsAnEqualResultEachTime() {
        Geocoder g = new CachingGeocoder(FixtureGeocoderLoader.geocoder(), new InMemoryGeocodeCache());
        PlaceQuery q = PlaceQuery.of("Springfield");
        assertThat(g.geocode(q)).isEqualTo(g.geocode(q));
    }

    @Test
    void zoneResolutionIsDeterministic() {
        var r = new TimeshapeTimeZoneResolver();
        assertThat(r.resolve(48.8566, 2.3522).zone()).isEqualTo(ZoneId.of("Europe/Paris"));
        assertThat(r.resolve(48.8566, 2.3522)).isEqualTo(r.resolve(48.8566, 2.3522));
    }
}
