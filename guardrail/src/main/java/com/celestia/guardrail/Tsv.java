package com.celestia.guardrail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reader for this module's own classpath TSVs: {@code #} comment lines, one
 * header line, then one record per line. Cells unescape {@code \\},
 * {@code \t} and {@code \n}.
 *
 * <p><b>Why this duplicates {@code core.toolkit.Tsv}.</b> That reader is
 * package-private, and widening {@code core}'s API to share forty lines would
 * export an internal file format from the module that owns the toolkit — a
 * larger commitment than the duplication costs. The two readers are deliberately
 * independent: this one serves the guardrail's own small tables, and a change to
 * the toolkit's import format must not silently change how hostile phrases
 * parse. See plan.md "Structure Decision".
 */
final class Tsv {

    private Tsv() {}

    /** One parsed file: its header and its records, both already unescaped. */
    record Table(List<String> header, List<List<String>> records) {
        Table {
            header = List.copyOf(header);
            records = records.stream().map(List::copyOf).toList();
        }

        /** The one-based index of {@code column}, or -1 when absent. */
        int columnIndex(String column) {
            return header.indexOf(column);
        }

        /** Cell {@code column} of {@code record}, or {@code ""} when short. */
        String cell(List<String> record, int index) {
            return index >= 0 && index < record.size() ? record.get(index) : "";
        }
    }

    /** Parse the classpath resource at {@code path}. */
    static Table read(String path) {
        try (InputStream in = Tsv.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("guardrail resource not found on the classpath: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return parse(reader, path);
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not read guardrail resource: " + path, e);
        }
    }

    private static Table parse(BufferedReader reader, String path) throws IOException {
        List<String> header = null;
        List<List<String>> records = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#") || line.isBlank()) {
                continue;
            }
            List<String> cells = splitLine(line);
            if (header == null) {
                header = cells;
            } else {
                records.add(cells);
            }
        }
        if (header == null) {
            throw new IllegalStateException("guardrail resource has no header line: " + path);
        }
        return new Table(header, records);
    }

    /** Split on unescaped tabs, then unescape each cell. */
    private static List<String> splitLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                char next = line.charAt(++i);
                switch (next) {
                    case 't' -> cell.append('\t');
                    case 'n' -> cell.append('\n');
                    case '\\' -> cell.append('\\');
                    default -> cell.append('\\').append(next);
                }
            } else if (c == '\t') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }
}
