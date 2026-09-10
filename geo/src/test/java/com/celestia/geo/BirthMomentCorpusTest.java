package com.celestia.geo;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.geo.place.PlaceCandidate;
import com.celestia.geo.time.TimeZoneResolver;
import com.celestia.geo.time.TimeshapeTimeZoneResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * SC-001 / SC-002 / SC-004 — every birth-moment in the checked-in corpus resolves
 * to the recorded instant (to the second), zone, offset and flags. The
 * {@code expected} block was produced independently by
 * {@code tools/ephe-crosscheck/compute_golden.py --birthmoments} (Python
 * {@code zoneinfo} + {@code timezonefinder}).
 */
class BirthMomentCorpusTest {

    private static final TimeZoneResolver ZONES = new TimeshapeTimeZoneResolver();
    private static final BirthMomentResolver RESOLVER = new BirthMomentResolver(ZONES);

    @TestFactory
    List<DynamicTest> corpus() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root;
        try (InputStream in = getClass().getResourceAsStream("/birthmoments/corpus.json")) {
            root = mapper.readTree(in);
        }

        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode e : root.get("births")) {
            tests.add(DynamicTest.dynamicTest(e.get("id").asText(), () -> check(e)));
        }
        return tests;
    }

    private static void check(JsonNode e) {
        double lat = e.get("latitude").asDouble();
        double lon = e.get("longitude").asDouble();
        PlaceCandidate place = new PlaceCandidate(
                "corpus:" + e.get("id").asText(), e.path("place_query").asText("place"),
                "", "", lat, lon, "corpus");

        LocalDate date = LocalDate.parse(e.get("birth_date").asText());
        JsonNode t = e.get("birth_time");
        LocalTime time = (t == null || t.isNull()) ? null : LocalTime.parse(pad(t.asText()));
        ZoneId stated = e.hasNonNull("stated_zone") ? ZoneId.of(e.get("stated_zone").asText()) : null;

        ResolvedBirth rb = RESOLVER.resolve(place, date, time, stated);
        JsonNode exp = e.get("expected");

        assertThat(rb.zoneId().getId()).as("zone_id").isEqualTo(exp.get("zone_id").asText());
        assertThat(rb.offsetApplied().getId())
                .as("offset_applied").isEqualTo(exp.get("offset_applied").asText());
        assertThat(rb.instant())
                .as("birth_utc").isEqualTo(Instant.parse(exp.get("birth_utc").asText()));

        Set<String> gotFlags = rb.flags().stream().map(Enum::name).collect(Collectors.toSet());
        Set<String> wantFlags = StreamSupport.stream(exp.get("flags").spliterator(), false)
                .map(JsonNode::asText).collect(Collectors.toSet());
        assertThat(gotFlags).as("flags").isEqualTo(wantFlags);
    }

    private static String pad(String hhmm) {
        return hhmm.length() == 5 ? hhmm : "0" + hhmm;
    }
}
