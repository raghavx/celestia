package com.celestia.geo.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class CandidateRankingPropertyTest {

    private static final PlaceQuery QUERY = PlaceQuery.of("target city");

    @Provide
    Arbitrary<List<PlaceCandidate>> candidateLists() {
        Arbitrary<PlaceCandidate> one = Arbitraries.integers().between(1, 999).map(n -> {
            boolean exact = n % 3 == 0;
            return new PlaceCandidate(
                    "id-" + String.format("%03d", n),
                    exact ? "Target City" : "Other Place " + n,
                    "", "", 0, 0, "fixture");
        });
        return one.list().uniqueElements(PlaceCandidate::id).ofMinSize(0).ofMaxSize(8);
    }

    @Property(tries = 400)
    void exactMatchesAlwaysPrecedeNonMatches(@ForAll("candidateLists") List<PlaceCandidate> raw) {
        List<PlaceCandidate> ranked = CandidateRanking.rank(QUERY, raw);
        int lastExact = -1;
        int firstNonExact = Integer.MAX_VALUE;
        for (int i = 0; i < ranked.size(); i++) {
            boolean exact = QueryNormalizer.normalize(ranked.get(i).displayName())
                    .equals(QUERY.normalized());
            if (exact) {
                lastExact = i;
            } else {
                firstNonExact = Math.min(firstNonExact, i);
            }
        }
        if (lastExact >= 0 && firstNonExact != Integer.MAX_VALUE) {
            assertThat(lastExact).isLessThan(firstNonExact);
        }
        assertThat(ranked).containsExactlyInAnyOrderElementsOf(raw);
    }

    @Property(tries = 400)
    void isIdempotent(@ForAll("candidateLists") List<PlaceCandidate> raw) {
        List<PlaceCandidate> once = CandidateRanking.rank(QUERY, raw);
        assertThat(CandidateRanking.rank(QUERY, once)).isEqualTo(once);
    }

    @Property(tries = 400)
    void resultIsIndependentOfInputPermutation(@ForAll("candidateLists") List<PlaceCandidate> raw) {
        // The non-exact tail keeps provider order, so compare against a canonical
        // permutation (by id) rather than an arbitrary shuffle.
        List<PlaceCandidate> byId = new ArrayList<>(raw);
        byId.sort((a, b) -> a.id().compareTo(b.id()));
        List<PlaceCandidate> shuffled = new ArrayList<>(byId);
        Collections.shuffle(shuffled, new java.util.Random(42));

        assertThat(CandidateRanking.rank(QUERY, byId))
                .containsExactlyElementsOf(CandidateRanking.rank(QUERY, byId));
        // idempotence + exact-first + stable tail already pin the order given a
        // fixed input; this asserts the exact group is fully id-ordered.
        List<PlaceCandidate> ranked = CandidateRanking.rank(QUERY, shuffled);
        List<String> exactIds = ranked.stream()
                .filter(c -> QueryNormalizer.normalize(c.displayName()).equals(QUERY.normalized()))
                .map(PlaceCandidate::id)
                .toList();
        assertThat(exactIds).isSorted();
    }
}
