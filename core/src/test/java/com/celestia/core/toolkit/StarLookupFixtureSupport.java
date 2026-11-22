package com.celestia.core.toolkit;

import com.celestia.core.lordage.Nakshatra;
import com.celestia.core.lordage.Sign;
import com.celestia.ephemeris.Graha;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the look-up fixture by iterating the engine's own enums (SPEC-010 T035).
 *
 * <p>Covers every key the look-ups must be total over — all 27&times;4
 * nakshatra-quarters, all 72 ordered friendship pairs, all 9 grahas (twice:
 * characteristics and direction) and all 12 signs — with placeholder text. That
 * makes the structural half of SC-004 testable without the licensed workbook;
 * the real text waits on T047.
 *
 * <p>Generated rather than hand-authored so that adding a constant to
 * {@code Nakshatra}, {@code Graha} or {@code Sign} cannot leave a silent hole.
 */
final class StarLookupFixtureSupport {

    static final String NAME = "star-fixture";

    private StarLookupFixtureSupport() {}

    /** Write the fixture root and return the classpath root to load it from. */
    static String write() {
        ToolkitFixtures.writeRelease(NAME, 0, 0);
        ToolkitFixtures.writeTsv(
                NAME,
                "manifest.tsv",
                List.of("order", "id", "title", "group", "kind", "sensitivity", "answerable"),
                List.of());

        // One row per nakshatra-quarter: 108 rows, so each (star, quarter) resolves once.
        List<List<String>> professions = new ArrayList<>();
        List<List<String>> diseases = new ArrayList<>();
        for (Nakshatra nakshatra : Nakshatra.values()) {
            for (int quarter = 1; quarter <= 4; quarter++) {
                String label = nakshatra.name() + " " + quarter + "Q";
                professions.add(List.of(
                        nakshatra.name(),
                        String.valueOf(quarter),
                        label,
                        "profession for " + label,
                        "classical profession for " + label));
                diseases.add(List.of(
                        nakshatra.name(),
                        String.valueOf(quarter),
                        label,
                        "body part for " + label,
                        "disease for " + label));
            }
        }
        ToolkitFixtures.writeTsv(
                NAME,
                "tables/star-professions.tsv",
                List.of("nakshatra", "quarters", "label", "professions", "classicalProfessions"),
                professions);
        ToolkitFixtures.writeTsv(
                NAME,
                "tables/star-diseases.tsv",
                List.of("nakshatra", "quarters", "label", "bodyParts", "diseases"),
                diseases);

        // All 72 ordered pairs, so friendship(a, b) never hits a hole.
        List<List<String>> friendships = new ArrayList<>();
        for (Graha of : Graha.values()) {
            for (Graha towards : Graha.values()) {
                if (of != towards) {
                    friendships.add(List.of(of.name(), towards.name(), Friendship.NEUTRAL.name()));
                }
            }
        }
        ToolkitFixtures.writeTsv(
                NAME, "tables/planet-friendship.tsv", List.of("of", "towards", "relation"), friendships);

        // Planet x house grids.
        List<List<String>> signification = new ArrayList<>();
        List<List<String>> education = new ArrayList<>();
        for (Graha graha : Graha.values()) {
            for (int house = 1; house <= 12; house++) {
                signification.add(List.of(graha.name(), String.valueOf(house), graha + " in house " + house));
                education.add(List.of(graha.name(), String.valueOf(house), graha + " stream for house " + house));
            }
        }
        ToolkitFixtures.writeTsv(
                NAME, "tables/planet-house-signification.tsv", List.of("graha", "house", "text"), signification);
        ToolkitFixtures.writeTsv(
                NAME, "tables/higher-education-streams.tsv", List.of("graha", "house", "text"), education);

        // Single-key tables (2026-11-23 clarification): planet alone, sign alone.
        List<List<String>> characteristics = new ArrayList<>();
        List<List<String>> directions = new ArrayList<>();
        for (Graha graha : Graha.values()) {
            characteristics.add(List.of(graha.name(), "characteristics of " + graha));
            directions.add(List.of(graha.name(), "direction of " + graha));
        }
        ToolkitFixtures.writeTsv(NAME, "tables/planet-characteristics.tsv", List.of("graha", "text"), characteristics);
        ToolkitFixtures.writeTsv(NAME, "tables/planet-direction.tsv", List.of("graha", "text"), directions);

        List<List<String>> zodiac = new ArrayList<>();
        for (Sign sign : Sign.values()) {
            zodiac.add(List.of(sign.name(), "characteristics of " + sign));
        }
        ToolkitFixtures.writeTsv(NAME, "tables/zodiac-characteristics.tsv", List.of("sign", "text"), zodiac);

        // House- and cusp-indexed text.
        List<List<String>> houses = new ArrayList<>();
        List<List<String>> relationships = new ArrayList<>();
        for (int house = 1; house <= 12; house++) {
            houses.add(List.of(String.valueOf(house), "meaning of house " + house));
            relationships.add(List.of(String.valueOf(house), "relation for cusp " + house));
        }
        ToolkitFixtures.writeTsv(NAME, "tables/house-meanings.tsv", List.of("house", "text"), houses);
        ToolkitFixtures.writeTsv(
                NAME, "tables/relationship-rotation.tsv", List.of("cusp", "text"), relationships);

        return ToolkitFixtures.root(NAME);
    }
}
