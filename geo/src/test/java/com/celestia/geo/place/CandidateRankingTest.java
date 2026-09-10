package com.celestia.geo.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateRankingTest {

    private static PlaceCandidate c(String id, String name) {
        return new PlaceCandidate(id, name, "", "", 0, 0, "fixture");
    }

    @Test
    void anExactNormalisedNameMatchSortsFirst() {
        PlaceQuery q = PlaceQuery.of("london");
        List<PlaceCandidate> ranked = CandidateRanking.rank(
                q, List.of(c("2", "London, Ontario"), c("1", "London")));
        assertThat(ranked).extracting(PlaceCandidate::id).containsExactly("1", "2");
    }

    @Test
    void nonMatchesKeepProviderOrder() {
        PlaceQuery q = PlaceQuery.of("springfield, xx");
        List<PlaceCandidate> raw = List.of(
                c("il", "Springfield, Illinois"),
                c("mo", "Springfield, Missouri"),
                c("ma", "Springfield, Massachusetts"));
        assertThat(CandidateRanking.rank(q, raw)).isEqualTo(raw);
    }

    @Test
    void multipleExactMatchesAreOrderedByIdSoTheGroupIsPermutationIndependent() {
        PlaceQuery q = PlaceQuery.of("paris");
        List<PlaceCandidate> a = CandidateRanking.rank(q, List.of(c("z", "Paris"), c("a", "Paris")));
        List<PlaceCandidate> b = CandidateRanking.rank(q, List.of(c("a", "Paris"), c("z", "Paris")));
        assertThat(a).extracting(PlaceCandidate::id).containsExactly("a", "z");
        assertThat(a).isEqualTo(b);
    }

    @Test
    void isIdempotent() {
        PlaceQuery q = PlaceQuery.of("london");
        List<PlaceCandidate> raw = List.of(c("2", "London, Ontario"), c("1", "London"), c("3", "Londonderry"));
        List<PlaceCandidate> once = CandidateRanking.rank(q, raw);
        assertThat(CandidateRanking.rank(q, once)).isEqualTo(once);
    }

    @Test
    void neverCollapsesTheList() {
        PlaceQuery q = PlaceQuery.of("london");
        assertThat(CandidateRanking.rank(q, List.of(c("1", "London"), c("2", "London, Ontario"))))
                .hasSize(2);
    }
}
