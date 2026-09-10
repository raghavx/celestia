package com.celestia.agent.persistence.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.geo.place.GeocodeResult;
import com.celestia.geo.place.PlaceCandidate;
import com.celestia.geo.place.PlaceQuery;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T017 — no container. */
class GeocodeResultCodecTest {

    private final GeocodeResultCodec codec = new GeocodeResultCodec();

    @Test
    void roundTripsTheCandidateList() {
        PlaceQuery q = PlaceQuery.of("Springfield");
        GeocodeResult original = new GeocodeResult(q, List.of(
                new PlaceCandidate("il", "Springfield, Illinois", "United States", "Illinois",
                        39.7817, -89.6501, "fixture"),
                new PlaceCandidate("mo", "Springfield, Missouri", "United States", "Missouri",
                        37.2089, -93.2923, "fixture")));

        GeocodeResult reloaded = codec.read(codec.write(q.normalized(), original)).orElseThrow();

        assertThat(reloaded.candidates()).isEqualTo(original.candidates());
        assertThat(reloaded.query().normalized()).isEqualTo("springfield");
    }

    @Test
    void emptyCandidateListRoundTrips() {
        PlaceQuery q = PlaceQuery.of("nowhere-xyz");
        GeocodeResult empty = new GeocodeResult(q, List.of());
        assertThat(codec.read(codec.write(q.normalized(), empty)).orElseThrow().candidates()).isEmpty();
    }

    @Test
    void aCorruptPayloadReadsAsEmpty() {
        assertThat(codec.read("{ broken")).isEmpty();
        assertThat(codec.read("{\"schema\":2,\"query\":{},\"candidates\":[]}")).isEmpty();
    }
}
