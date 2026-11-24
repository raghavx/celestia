package com.celestia.guardrail;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The one ordered normalisation pipeline every inbound message passes through
 * (FR-005, FR-011, research.md §2).
 *
 * <p>It produces two things, and the difference between them is a security
 * property rather than a convenience:
 *
 * <ol>
 *   <li>a <b>normalised view</b> — NFKC, invisible characters stripped,
 *       confusables folded, lower-cased, whitespace collapsed;
 *   <li>a set of <b>decoded candidates</b> — base64 or hex runs that decoded to
 *       printable text.
 * </ol>
 *
 * <p><b>The asymmetry.</b> Hostile rules match against the normalised view
 * <em>and</em> every decoded candidate. In-scope evidence is sought in the
 * normalised view <em>alone</em>. If decoded text could supply in-scope
 * evidence, an attacker would base64-encode the word "marriage" to buy passage
 * through the topic gate, and the gate would have helped them. Decoding may only
 * ever incriminate, never exonerate (FR-005).
 *
 * <p>Pure and deterministic: no clock, no locale sensitivity ({@link
 * Locale#ROOT} throughout), no state.
 */
final class Normaliser {

    /** Shortest run worth trying to decode. Below this, false positives dominate. */
    private static final int MIN_ENCODED_RUN = 16;

    /** A decoded candidate must be at least this long to be worth matching. */
    private static final int MIN_DECODED_LENGTH = 4;

    /**
     * Longest run worth decoding. A real injected payload is short; a run longer
     * than this is padding, and decoding it costs more than it can ever catch.
     */
    private static final int MAX_ENCODED_RUN = 512;

    /**
     * A payload has varied characters. A run of one repeated character — the
     * classic way to pad a message past the bound — cannot encode a phrase, so
     * screening it out is both faster and more accurate.
     */
    private static final int MIN_DISTINCT_CHARS = 4;

    private static final String FOLDS_RESOURCE = "com/celestia/guardrail/homoglyph-folds.tsv";

    private static final Map<Integer, String> FOLDS = loadFolds();

    private Normaliser() {}

    /**
     * The normalised view plus the decoded candidates, which are kept apart on
     * purpose — see the class note.
     *
     * @param normalised the view all matching uses
     * @param decodedCandidates decoded payloads, for hostile rules only
     */
    record NormalisedMessage(String normalised, List<String> decodedCandidates) {
        NormalisedMessage {
            decodedCandidates = List.copyOf(decodedCandidates);
        }

        /** The normalised view and every decoded candidate — hostile matching only. */
        List<String> hostileViews() {
            List<String> views = new ArrayList<>(decodedCandidates.size() + 1);
            views.add(normalised);
            views.addAll(decodedCandidates);
            return views;
        }
    }

    /** Run the pipeline of research.md §2 in order. */
    static NormalisedMessage normalise(String raw) {
        String nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        String visible = stripInvisible(nfkc);
        String folded = foldConfusables(visible);

        // Candidates are extracted BEFORE lower-casing: base64 is case-sensitive,
        // so decoding a lower-cased run would decode something the sender never wrote.
        List<String> candidates = decodeCandidates(folded);

        String normalised = collapse(folded.toLowerCase(Locale.ROOT));
        return new NormalisedMessage(normalised, candidates);
    }

    /** Lower-case and collapse only — for text that is already a decoded payload. */
    private static String finish(String decoded) {
        return collapse(foldConfusables(stripInvisible(
                        Normalizer.normalize(decoded, Normalizer.Form.NFKC)))
                .toLowerCase(Locale.ROOT));
    }

    /**
     * Strip what the eye cannot see: zero-width characters, bidi controls,
     * variation selectors and the other formatting code points an attacker uses
     * to break a phrase up without changing how it reads.
     */
    private static String stripInvisible(String s) {
        StringBuilder out = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            if (!isInvisible(cp)) {
                out.appendCodePoint(cp);
            }
        });
        return out.toString();
    }

    private static boolean isInvisible(int cp) {
        if (cp < 0x80) {
            return false; // no ASCII code point is invisible; the common case, taken first
        }
        return switch (cp) {
            case 0x00AD, // soft hyphen
                    0x034F, // combining grapheme joiner
                    0x061C, // arabic letter mark
                    0x180E, // mongolian vowel separator
                    0x200B, 0x200C, 0x200D, // zero-width space / non-joiner / joiner
                    0x200E, 0x200F, // LRM / RLM
                    0x2060, 0x2061, 0x2062, 0x2063, 0x2064, // word joiner, invisible operators
                    0x2066, 0x2067, 0x2068, 0x2069, // isolates
                    0x3164, // hangul filler
                    0xFEFF -> // BOM / zero-width no-break space
                    true;
            default ->
                (cp >= 0x202A && cp <= 0x202E) // bidi embedding / override
                        || (cp >= 0xFE00 && cp <= 0xFE0F) // variation selectors
                        || (cp >= 0xE0100 && cp <= 0xE01EF) // variation selectors supplement
                        || (cp >= 0xE0020 && cp <= 0xE007F) // tag characters
                        || Character.getType(cp) == Character.FORMAT;
        };
    }

    /** Map confusable glyphs to their Latin equivalents, from the reviewed table. */
    private static String foldConfusables(String s) {
        StringBuilder out = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            String fold = FOLDS.get(cp);
            if (fold != null) {
                out.append(fold);
            } else {
                out.appendCodePoint(cp);
            }
        });
        return out.toString();
    }

    /** Collapse whitespace runs to one space and runs of repeated punctuation to one mark. */
    private static String collapse(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int lastPunct = -1;
        int punctRun = 0;
        boolean pendingSpace = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                pendingSpace = !out.isEmpty();
                lastPunct = -1;
                punctRun = 0;
                continue;
            }
            if (pendingSpace) {
                out.append(' ');
                pendingSpace = false;
            }
            boolean punct = !Character.isLetterOrDigit(c);
            if (punct && c == lastPunct) {
                if (++punctRun >= 2) {
                    continue; // third and later repeat of the same mark
                }
            } else {
                punctRun = 0;
            }
            lastPunct = punct ? c : -1;
            out.append(c);
        }
        return out.toString();
    }

    /**
     * Find base64 and hex runs that decode to printable text.
     *
     * <p>Deliberately conservative: a run must be long enough to be a payload
     * rather than a word, and must decode to text a human could have typed.
     * Anything else is dropped, because a false candidate costs a wasted match
     * while a false <em>decode</em> could invent a phrase nobody sent.
     */
    private static List<String> decodeCandidates(String s) {
        List<String> candidates = new ArrayList<>();
        collectRuns(s, Normaliser::isBase64Char, run -> decodeBase64(run).ifPresent(candidates::add));
        collectRuns(s, Normaliser::isHexChar, run -> decodeHex(run).ifPresent(candidates::add));
        return candidates.stream().distinct().toList();
    }

    private interface RunSink {
        void accept(String run);
    }

    private interface CharTest {
        boolean test(char c);
    }

    private static void collectRuns(String s, CharTest test, RunSink sink) {
        int start = -1;
        for (int i = 0; i <= s.length(); i++) {
            boolean in = i < s.length() && test.test(s.charAt(i));
            if (in && start < 0) {
                start = i;
            } else if (!in && start >= 0) {
                int length = i - start;
                if (length >= MIN_ENCODED_RUN && length <= MAX_ENCODED_RUN) {
                    String run = s.substring(start, i);
                    if (run.chars().distinct().count() >= MIN_DISTINCT_CHARS) {
                        sink.accept(run);
                    }
                }
                start = -1;
            }
        }
    }

    private static boolean isBase64Char(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '+' || c == '/' || c == '=' || c == '-' || c == '_';
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static java.util.Optional<String> decodeBase64(String run) {
        for (Base64.Decoder decoder : List.of(Base64.getMimeDecoder(), Base64.getUrlDecoder())) {
            try {
                byte[] bytes = decoder.decode(padToQuads(run));
                java.util.Optional<String> text = printable(bytes);
                if (text.isPresent()) {
                    return text.map(Normaliser::finish);
                }
            } catch (IllegalArgumentException ignored) {
                // not this encoding; try the next
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Base64 needs a length that is a multiple of four. <b>Pad, never truncate</b>
     * — truncating drops the tail of the payload, which silently decodes the
     * attack minus its last character and so misses the phrase.
     */
    private static String padToQuads(String run) {
        String body = run.replace("=", "");
        int remainder = body.length() % 4;
        if (remainder == 1) {
            // A single leftover character cannot encode a byte; drop just that one.
            body = body.substring(0, body.length() - 1);
            remainder = 0;
        }
        return remainder == 0 ? body : body + "=".repeat(4 - remainder);
    }

    private static java.util.Optional<String> decodeHex(String run) {
        int length = run.length() - (run.length() % 2);
        byte[] bytes = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            bytes[i / 2] = (byte) Integer.parseInt(run.substring(i, i + 2), 16);
        }
        return printable(bytes).map(Normaliser::finish);
    }

    /**
     * Accept a decode only when it looks like something a person wrote: mostly
     * printable ASCII, and long enough to carry a phrase.
     */
    private static java.util.Optional<String> printable(byte[] bytes) {
        if (bytes.length < MIN_DECODED_LENGTH) {
            return java.util.Optional.empty();
        }
        int printable = 0;
        for (byte b : bytes) {
            int c = b & 0xFF;
            if (c == 0x09 || c == 0x0A || c == 0x0D || (c >= 0x20 && c <= 0x7E)) {
                printable++;
            }
        }
        if (printable * 10 < bytes.length * 9) { // under 90% printable
            return java.util.Optional.empty();
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        return text.chars().anyMatch(Character::isLetter)
                ? java.util.Optional.of(text)
                : java.util.Optional.empty();
    }

    private static Map<Integer, String> loadFolds() {
        Tsv.Table table = Tsv.read(FOLDS_RESOURCE);
        int confusable = table.columnIndex("confusable");
        int latin = table.columnIndex("latin");
        if (confusable < 0 || latin < 0) {
            throw new IllegalStateException(FOLDS_RESOURCE + " needs 'confusable' and 'latin' columns");
        }
        Map<Integer, String> folds = new HashMap<>();
        for (List<String> record : table.records()) {
            String from = table.cell(record, confusable);
            String to = table.cell(record, latin);
            if (from.isEmpty() || to.isEmpty()) {
                throw new IllegalStateException("homoglyph fold with an empty side: " + record);
            }
            folds.put(from.codePointAt(0), to);
        }
        return Map.copyOf(folds);
    }
}
