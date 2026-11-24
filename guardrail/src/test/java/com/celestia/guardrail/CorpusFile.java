package com.celestia.guardrail;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a corpus file the way a reviewer writes one: {@code #} comments, a
 * header, then one case per line (contracts/corpus-format.md).
 *
 * <p>Shared by both corpus tests so that <b>adding a line is the whole
 * change</b> — no Java touched (FR-020, SC-009).
 */
final class CorpusFile {

    private CorpusFile() {}

    /** One case, addressed by column name so a new column cannot shift the others. */
    record Case(Map<String, String> cells) {
        String get(String column) {
            return cells.getOrDefault(column, "");
        }

        String id() {
            return get("id");
        }
    }

    static List<Case> read(String resource) {
        try (InputStream in = CorpusFile.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("corpus not found on the classpath: " + resource);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                List<String> lines = reader.lines()
                        .filter(line -> !line.startsWith("#") && !line.isBlank())
                        .toList();
                if (lines.isEmpty()) {
                    throw new IllegalStateException("corpus has no header: " + resource);
                }
                String[] header = lines.get(0).split("\t", -1);
                List<Case> cases = new ArrayList<>();
                for (String line : lines.subList(1, lines.size())) {
                    String[] cells = line.split("\t", -1);
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int i = 0; i < header.length; i++) {
                        row.put(header[i], i < cells.length ? cells[i] : "");
                    }
                    cases.add(new Case(row));
                }
                return List.copyOf(cases);
            }
        } catch (Exception e) {
            throw new IllegalStateException("could not read corpus: " + resource, e);
        }
    }

    /** How many data rows the file holds — the figure SC-009's assertion compares against. */
    static int dataRows(String resource) {
        return read(resource).size();
    }
}
