package com.celestia.core.toolkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** T013 — the loader and its fail-fast validation (data-model.md "Validation at load"). */
class KpToolkitLoadTest {

    private static KpToolkit toolkit;

    @BeforeAll
    static void loadFixture() {
        toolkit = KpToolkit.load(SheetFixtureSupport.write());
    }

    @Test
    void loadsTheFixtureRelease() {
        assertThat(toolkit.version().version()).isEqualTo("2026-09-12");
        assertThat(toolkit.version().id()).isEqualTo("toolkit-2026-09-12");
        assertThat(toolkit.sheets()).isNotEmpty();
    }

    @Test
    void sheetsAreInWorkbookOrderAndFindableById() {
        assertThat(toolkit.sheets()).isSortedAccordingTo(
                java.util.Comparator.comparingInt(ToolkitSheet::order));
        assertThat(toolkit.sheet("time-of-event")).isPresent();
        assertThat(toolkit.sheet("no-such-sheet")).isEmpty();
    }

    @Test
    void sheetsCanBeListedByTopicGroup() {
        assertThat(toolkit.sheets(TopicGroup.HEALTH_MEDICAL))
                .isNotEmpty()
                .allSatisfy(sheet -> assertThat(sheet.group()).isEqualTo(TopicGroup.HEALTH_MEDICAL));
    }

    @Test
    void everySheetKeepsItsRowsVerbatimAndInOrder() {
        // FR-007: a procedure must come back in the order it was written.
        ToolkitSheet procedure = toolkit.sheet("time-of-event").orElseThrow();
        assertThat(procedure.rows().get(0).get(0)).isEqualTo(SheetFixtureSupport.PROCEDURE_STEP);
        assertThat(procedure.rows().get(1).get(0)).contains("Step 2");
        assertThat(procedure.rows().get(2).get(0)).contains("Step 3");
    }

    @Test
    void aManifestThatDisagreesWithThePropertiesFailsFast() {
        String broken = SheetFixtureSupport.writeBroken();
        assertThatThrownBy(() -> KpToolkit.load(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declares 99 sheets");
    }

    @Test
    void anAbsentRootFailsWithAnActionableMessage() {
        assertThatThrownBy(() -> KpToolkit.load("com/celestia/core/toolkit/not-imported"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("import_toolkit.py");
    }
}
