package com.celestia.agent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.agent.persistence.repository.GeocodeCacheRepository;
import com.celestia.agent.persistence.store.JpaGeocodeCache;
import com.celestia.geo.place.GeocodeResult;
import com.celestia.geo.place.PlaceCandidate;
import com.celestia.geo.place.PlaceQuery;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

/** SPEC-008 US4 / SC-005 — the persistent geo.GeocodeCache. */
@Transactional
class JpaGeocodeCacheIT extends PersistenceIT {

    @Autowired
    JpaGeocodeCache cache;

    @Autowired
    GeocodeCacheRepository repo;

    private static GeocodeResult springfield() {
        PlaceQuery q = PlaceQuery.of("Springfield");
        return new GeocodeResult(q, List.of(
                new PlaceCandidate("il", "Springfield, Illinois", "United States", "Illinois",
                        39.7817, -89.6501, "fixture"),
                new PlaceCandidate("mo", "Springfield, Missouri", "United States", "Missouri",
                        37.2089, -93.2923, "fixture")));
    }

    @Test
    void missReturnsEmpty() {
        assertThat(cache.get("nowhere-xyz")).isEmpty();
    }

    @Test
    void hitReturnsAnEqualCandidateList() {
        GeocodeResult original = springfield();
        cache.put("springfield", original);

        GeocodeResult reloaded = cache.get("springfield").orElseThrow();
        assertThat(reloaded.candidates()).isEqualTo(original.candidates());
    }

    @Test
    void putForAnExistingKeyUpdatesInPlace() {
        cache.put("paris", new GeocodeResult(PlaceQuery.of("Paris"),
                List.of(new PlaceCandidate("fr", "Paris", "France", "", 48.85, 2.35, "fixture"))));
        cache.put("paris", new GeocodeResult(PlaceQuery.of("Paris"),
                List.of(new PlaceCandidate("tx", "Paris, Texas", "United States", "Texas",
                        33.66, -95.55, "fixture"))));

        assertThat(repo.findAll().stream().filter(e -> e.getQueryNorm().equals("paris")).count())
                .isEqualTo(1);
        assertThat(cache.get("paris").orElseThrow().candidates())
                .singleElement()
                .satisfies(c -> assertThat(c.id()).isEqualTo("tx"));
    }
}
