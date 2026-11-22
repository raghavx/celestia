package com.celestia.core.toolkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.core.horary.Horary249;
import com.celestia.core.horary.HoraryArc;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * T029 — the toolkit's horary table against the engine's (SPEC-010 SC-003).
 *
 * <p>The toolkit's 249 table is the reference KP students check against, so a
 * disagreement with the engine means one of them is wrong and the product must
 * not ship either (Constitution III). The structural half runs against a fixture
 * generated from {@link Horary249} itself, so it holds on any checkout; the
 * real workbook's own lords are cross-checked in the second group, which skips
 * if the toolkit has not been imported.
 */
class ToolkitHoraryCrossCheckTest {

    private static KpToolkit toolkit;

    @BeforeAll
    static void loadFixture() {
        toolkit = KpToolkit.load(HoraryFixtureSupport.write());
    }

    @Test
    void allTwoHundredFortyNineRowsAgreeWithTheEngineLordForLord() {
        assertThat(toolkit.horaryTable()).hasSize(249);
        for (int number = 1; number <= 249; number++) {
            HoraryMindset row = toolkit.horary(number);
            HoraryArc arc = Horary249.arc(number);

            assertThat(row.number()).isEqualTo(number);
            assertThat(row.sign()).as("number %d sign", number).isEqualTo(arc.sign().name());
            assertThat(row.star())
                    .as("number %d star", number)
                    .isEqualTo(arc.lordChain().nakshatra().name());
            assertThat(row.signLord())
                    .as("number %d sign lord", number)
                    .isEqualTo(arc.lordChain().signLord());
            assertThat(row.starLord())
                    .as("number %d star lord", number)
                    .isEqualTo(arc.lordChain().starLord());
            assertThat(row.subLord()).as("number %d sub lord", number).isEqualTo(arc.subLord());
        }
    }

    @Test
    void everyNumberCarriesAMindset() {
        for (int number = 1; number <= 249; number++) {
            assertThat(toolkit.horary(number).mindset())
                    .as("number %d mindset", number)
                    .isNotBlank();
        }
    }

    @Test
    void theTableIsNumberedOneToTwoHundredFortyNineExactlyOnce() {
        assertThat(toolkit.horaryTable().stream().map(HoraryMindset::number).distinct().toList())
                .hasSize(249);
        assertThat(toolkit.horaryTable().stream()
                        .map(HoraryMindset::number)
                        .toList())
                .isSorted();
    }

    @Test
    void aNumberOutsideTheRangeIsRejectedRatherThanGuessed() {
        // US2 acceptance scenario 2: the user is asked for a valid number and no
        // reading is produced.
        assertThatThrownBy(() -> toolkit.horary(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1..249");
        assertThatThrownBy(() -> toolkit.horary(250)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> toolkit.horary(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------- the real toolkit

    @Test
    @EnabledIf("com.celestia.core.toolkit.RealToolkit#isImported")
    void theRealToolkitTableAgreesWithTheEngineOnAllTwoHundredFortyNineRows() {
        // SC-003, the real check: the workbook's own lords against the engine's.
        KpToolkit real = KpToolkit.bundled();
        assertThat(real.horaryTable()).hasSize(249);
        for (int number = 1; number <= 249; number++) {
            HoraryMindset row = real.horary(number);
            HoraryArc arc = Horary249.arc(number);
            assertThat(row.signLord()).as("number %d sign lord", number).isEqualTo(arc.lordChain().signLord());
            assertThat(row.starLord()).as("number %d star lord", number).isEqualTo(arc.lordChain().starLord());
            assertThat(row.subLord()).as("number %d sub lord", number).isEqualTo(arc.subLord());
            assertThat(row.mindset()).as("number %d mindset", number).isNotBlank();
        }
    }

    @Test
    @EnabledIf("com.celestia.core.toolkit.RealToolkit#isImported")
    void theMisNumberedRowIsReadAsHoraryOneHundredSeven() {
        // US2 acceptance scenario 1 / research.md §10: the 249Mindsets sheet
        // writes this row's number as "L07". The import reads it as 107, and
        // the row's own lords are what confirms the correction is right.
        HoraryMindset row = KpToolkit.bundled().horary(107);
        assertThat(row.sign()).isEqualTo("Virgo");
        assertThat(row.starLord()).isEqualTo(com.celestia.ephemeris.Graha.SUN);
        assertThat(row.subLord()).isEqualTo(com.celestia.ephemeris.Graha.JUPITER);
        assertThat(row.mindset()).isNotBlank();
    }

    @Test
    void aTableThatDoesNotCoverEveryNumberFailsTheLoad() {
        String name = "short-horary-fixture";
        ToolkitFixtures.writeRelease(name, 0, 0);
        ToolkitFixtures.writeTsv(
                name,
                "manifest.tsv",
                java.util.List.of("order", "id", "title", "group", "kind", "sensitivity", "answerable"),
                java.util.List.of());
        ToolkitFixtures.writeTsv(
                name,
                "tables/horary-249.tsv",
                java.util.List.of(
                        "number", "sign", "star", "fromDeg", "toDeg", "signLord", "starLord", "subLord", "mindset"),
                java.util.List.of(java.util.List.of(
                        "1", "ARIES", "ASHWINI", "0.0", "0.5", "MARS", "KETU", "KETU", "a mindset")));

        assertThatThrownBy(() -> KpToolkit.load(ToolkitFixtures.root(name)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("249");
    }
}
