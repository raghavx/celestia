package com.celestia.core.toolkit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reader for the toolkit's TSV resources: a {@code #} comment line, a header
 * line, then one record per line. Cells escape {@code \\}, {@code \t} and
 * {@code \n} (SPEC-010 research.md &sect;1).
 *
 * <p>TSV rather than JSON because {@code core} carries no runtime JSON library
 * (Constitution II keeps it lean) and because a one-rule-per-line diff is what
 * makes a toolkit release reviewable by hand.
 *
 * <p>Package-private: the resource format is an implementation detail of
 * {@link KpToolkit}, not part of its contract.
 */
final class Tsv {

    private Tsv() {}

    /** One parsed file: its header and its records, both already unescaped. */
    record Table(List<String> header, List<List<String>> records) {

        Table {
            header = List.copyOf(header);
            records = records.stream().map(List::copyOf).toList();
        }

        /** The 0-based index of {@code column}, or -1 when absent. */
        int indexOf(String column) {
            return header.indexOf(column);
        }
    }

    /**
     * Parse the classpath resource at {@code path}.
     *
     * @throws IllegalStateException if the resource is missing or has no header
     */
    static Table read(String path) {
        try (InputStream in = Tsv.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("toolkit resource not found on the classpath: " + path);
            }
            return parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)), path);
        } catch (IOException e) {
            throw new UncheckedIOException("failed reading toolkit resource " + path, e);
        }
    }

    /** True when {@code path} exists on the classpath. */
    static boolean exists(String path) {
        return Tsv.class.getClassLoader().getResource(path) != null;
    }

    private static Table parse(BufferedReader reader, String path) throws IOException {
        List<String> header = null;
        List<List<String>> records = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#")) {
                continue;
            }
            if (line.isEmpty()) {
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
            throw new IllegalStateException("toolkit resource has no header line: " + path);
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
