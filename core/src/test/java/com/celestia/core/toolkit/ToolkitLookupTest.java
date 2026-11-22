package com.celestia.core.toolkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.core.lordage.Nakshatra;
import com.celestia.core.lordage.Sign;
import com.celestia.ephemeris.Graha;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * T035 — the look-ups are total over their keys (SPEC-010 SC-004, FR-006).
 *
 * <p>"Total" is the point: these answer the most-asked questions about a chart,
 * and a hole would mean a user whose 10th sub lord lands in the missing quarter
 * silently gets nothing. Keys are precise (nakshatra + quarter, planet + house,
 * planet alone, sign alone), never text search.
 */
class ToolkitLookupTest {

    private static KpToolkit toolkit;

    @BeforeAll
    static void loadFixture() {
        toolkit = KpToolkit.load(StarLookupFixtureSupport.write());
    }

    @Test
    void allOneHundredEightNakshatraQuartersResolveInBothStarTables() {
        for (Nakshatra nakshatra : Nakshatra.values()) {
            for (int quarter = 1; quarter <= 4; quarter++) {
                assertThat(toolkit.starProfession(nakshatra, quarter))
                        .as("professions for %s quarter %d", nakshatra, quarter)
                        .isPresent();
                assertThat(toolkit.starDisease(nakshatra, quarter))
                        .as("diseases for %s quarter %d", nakshatra, quarter)
                        .isPresent();
            }
        }
        assertThat(Nakshatra.values()).hasSize(27); // 27 x 4 = the 108 of SC-004
    }

    @Test
    void aStarEntryKnowsWhichQuartersItCovers() {
        StarEntry entry = toolkit.starProfession(Nakshatra.KRITTIKA, 3).orElseThrow();
        assertThat(entry.nakshatra()).isEqualTo(Nakshatra.KRITTIKA);
        assertThat(entry.covers(3)).isTrue();
        assertThat(entry.primary()).isNotBlank();
    }

    @Test
    void aQuarterOutsideOneToFourIsRejected() {
        assertThatThrownBy(() -> toolkit.starProfession(Nakshatra.PUSHYA, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> toolkit.starProfession(Nakshatra.PUSHYA, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyPlanetAndHouseResolvesInBothPlanetHouseTables() {
        for (Graha graha : Graha.values()) {
            for (int house = 1; house <= 12; house++) {
                assertThat(toolkit.planetInHouse(graha, house))
                        .as("%s in house %d", graha, house)
                        .isPresent();
                assertThat(toolkit.higherEducation(graha, house))
                        .as("%s education stream for house %d", graha, house)
                        .isPresent();
            }
        }
    }

    @Test
    void allSeventyTwoOrderedFriendshipPairsArePresent() {
        int pairs = 0;
        for (Graha of : Graha.values()) {
            for (Graha towards : Graha.values()) {
                if (of != towards) {
                    assertThat(toolkit.friendship(of, towards))
                            .as("%s towards %s", of, towards)
                            .isNotNull();
                    pairs++;
                }
            }
        }
        assertThat(pairs).isEqualTo(72);
    }

    @Test
    void friendshipNeedsTwoDifferentGrahas() {
        assertThatThrownBy(() -> toolkit.friendship(Graha.SUN, Graha.SUN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allNinePlanetsResolveInCharacteristicsAndDirection() {
        // 2026-11-23 clarification: keyed by the planet alone - the same answer
        // for every user, no chart involved.
        for (Graha graha : Graha.values()) {
            assertThat(toolkit.planetCharacteristics(graha))
                    .as("characteristics of %s", graha)
                    .isNotBlank();
            assertThat(toolkit.planetDirection(graha))
                    .as("direction of %s", graha)
                    .isNotBlank();
        }
        assertThat(Graha.values()).hasSize(9);
    }

    @Test
    void allTwelveSignsResolveInZodiacCharacteristics() {
        for (Sign sign : Sign.values()) {
            assertThat(toolkit.zodiacCharacteristics(sign))
                    .as("characteristics of %s", sign)
                    .isNotBlank();
        }
        assertThat(Sign.values()).hasSize(12);
    }

    @Test
    void houseMeaningsAndRelationshipsCoverAllTwelve() {
        for (int house = 1; house <= 12; house++) {
            assertThat(toolkit.houseMeaning(house)).as("meaning of house %d", house).isNotBlank();
            assertThat(toolkit.relationship(house)).as("relation for cusp %d", house).isNotBlank();
        }
        assertThatThrownBy(() -> toolkit.houseMeaning(13)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> toolkit.relationship(0)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------- the real toolkit

    @Test
    @EnabledIf("com.celestia.core.toolkit.RealToolkit#isImported")
    void theRealStarTablesResolveAllOneHundredEightQuarters() {
        // SC-004, the real check. The two sheets group their rows by quarter in
        // different notations ("Krittika 2-3-4Q", "Karthikai 2, 3 and 4th
        // Quarters"), so totality here is what proves both were read correctly.
        KpToolkit real = KpToolkit.bundled();
        for (Nakshatra nakshatra : Nakshatra.values()) {
            for (int quarter = 1; quarter <= 4; quarter++) {
                assertThat(real.starProfession(nakshatra, quarter))
                        .as("real professions for %s quarter %d", nakshatra, quarter)
                        .isPresent();
                assertThat(real.starDisease(nakshatra, quarter))
                        .as("real diseases for %s quarter %d", nakshatra, quarter)
                        .isPresent();
            }
        }
    }

    @Test
    @EnabledIf("com.celestia.core.toolkit.RealToolkit#isImported")
    void theRealKrittikaQuarterThreeUsesTheTwoThreeFourRow() {
        // US3 acceptance scenario 1: a 10th sub lord in Krittika quarter 3 must
        // use the "Krittika 2-3-4Q" row, not the "1Q" one.
        StarEntry entry = KpToolkit.bundled().starProfession(Nakshatra.KRITTIKA, 3).orElseThrow();
        assertThat(entry.label()).contains("2-3-4");
        assertThat(entry.covers(3)).isTrue();
        assertThat(entry.primary()).isNotBlank();

        StarEntry first = KpToolkit.bundled().starProfession(Nakshatra.KRITTIKA, 1).orElseThrow();
        assertThat(first.label()).isNotEqualTo(entry.label());
    }

    @Test
    @EnabledIf("com.celestia.core.toolkit.RealToolkit#isImported")
    void theRealSingleKeyLookUpsCoverEveryPlanetAndSign() {
        // The 2026-11-23 clarification's three tables.
        KpToolkit real = KpToolkit.bundled();
        for (Graha graha : Graha.values()) {
            assertThat(real.planetCharacteristics(graha)).as("characteristics of %s", graha).isNotBlank();
            assertThat(real.planetDirection(graha)).as("direction of %s", graha).isNotBlank();
        }
        for (Sign sign : Sign.values()) {
            assertThat(real.zodiacCharacteristics(sign)).as("characteristics of %s", sign).isNotBlank();
        }
    }

    @Test
    @EnabledIf("com.celestia.core.toolkit.RealToolkit#isImported")
    void theRealFriendshipTableHoldsAllSeventyTwoPairs() {
        KpToolkit real = KpToolkit.bundled();
        for (Graha of : Graha.values()) {
            for (Graha towards : Graha.values()) {
                if (of != towards) {
                    assertThat(real.friendship(of, towards)).as("%s -> %s", of, towards).isNotNull();
                }
            }
        }
    }

    @Test
    void aStarTableWithAHoleFailsTheLoad() {
        // One missing quarter must stop the build, not surface as an empty answer.
        String name = "holed-star-fixture";
        ToolkitFixtures.writeRelease(name, 0, 0);
        ToolkitFixtures.writeTsv(
                name,
                "manifest.tsv",
                java.util.List.of("order", "id", "title", "group", "kind", "sensitivity", "answerable"),
                java.util.List.of());
        ToolkitFixtures.writeTsv(
                name,
                "tables/star-professions.tsv",
                java.util.List.of("nakshatra", "quarters", "label", "professions", "classicalProfessions"),
                java.util.List.of(java.util.List.of("ASHWINI", "1", "Ashwini 1Q", "a profession", "a classical one")));

        assertThatThrownBy(() -> KpToolkit.load(ToolkitFixtures.root(name)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("star-professions");
    }
}
