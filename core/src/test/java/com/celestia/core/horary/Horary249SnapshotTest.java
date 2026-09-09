package com.celestia.core.horary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * SPEC-005: the full 249-row table is pinned as a checked-in snapshot. The test
 * writes {@code src/test/resources/horary/horary-249.json} when it is missing (as
 * the golden harness does); after it is committed, any later diff is a table
 * change to review.
 */
class Horary249SnapshotTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RESOURCE = "/horary/horary-249.json";
    private static final Path SOURCE =
            Path.of("src/test/resources/horary/horary-249.json");

    private static ArrayNode currentTable() {
        ArrayNode rows = MAPPER.createArrayNode();
        for (HoraryArc a : Horary249.arcs()) {
            ObjectNode row = MAPPER.createObjectNode();
            row.put("number", a.number());
            row.put("start", round6(a.startDeg()));
            row.put("end", round6(a.endDeg()));
            row.put("sign", a.sign().name());
            row.put("sub_lord", a.subLord().name());
            rows.add(row);
        }
        return rows;
    }

    private static double round6(double d) {
        return Math.round(d * 1_000_000.0) / 1_000_000.0;
    }

    @Test
    void the249TableMatchesTheSnapshot() throws IOException {
        ArrayNode current = currentTable();

        try (InputStream in = Horary249SnapshotTest.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                Files.createDirectories(SOURCE.getParent());
                Files.writeString(SOURCE,
                        MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(current) + "\n");
                throw new AssertionError(
                        "wrote a new snapshot to " + SOURCE + " — review and commit it, then re-run");
            }
            JsonNode snapshot = MAPPER.readTree(in);
            assertThat(current).isEqualTo(snapshot);
        }
    }
}
