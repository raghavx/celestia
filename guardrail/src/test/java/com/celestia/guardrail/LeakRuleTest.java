package com.celestia.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.toolkit.KpToolkit;
import com.celestia.core.toolkit.ToolkitSheet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The title prose classification itself (FR-017, research.md §8).
 *
 * <p>{@code OutputStageTest} exercises the scan through example drafts; this
 * exercises the classification that decides which signal each title gets, so a
 * sheet the scan has never classified cannot slip through unnoticed.
 */
class LeakRuleTest {

    private static final OutputStage STAGE = OutputStage.bundled();
    private static final KpToolkit TOOLKIT = KpToolkit.bundled();

    @Test
    void everySheetTitleResolvesToExactlyOneClass() {
        Set<String> classified = new HashSet<>();
        classified.addAll(STAGE.titles(LeakRule.ProseClass.NON_PROSE));
        classified.addAll(STAGE.titles(LeakRule.ProseClass.PROSE_COLLIDING));

        for (ToolkitSheet sheet : TOOLKIT.sheets()) {
            assertThat(classified)
                    .as("sheet '%s' has no prose class — the scan cannot be trusted on a title "
                            + "it never classified", sheet.title())
                    .contains(sheet.title());
        }

        List<String> nonProse = STAGE.titles(LeakRule.ProseClass.NON_PROSE);
        List<String> prose = STAGE.titles(LeakRule.ProseClass.PROSE_COLLIDING);
        assertThat(nonProse).doesNotContainAnyElementsOf(prose);
    }

    @Test
    void theSplitIsDerivedNotAHardCount() {
        // A toolkit release that adds a sheet must change the numbers without
        // failing this test, so nothing here asserts 34 or 41.
        int total = TOOLKIT.sheets().size();
        int classified = STAGE.titles(LeakRule.ProseClass.NON_PROSE).size()
                + STAGE.titles(LeakRule.ProseClass.PROSE_COLLIDING).size();

        assertThat(classified).isGreaterThanOrEqualTo(total);
        assertThat(STAGE.titles(LeakRule.ProseClass.NON_PROSE)).isNotEmpty();
        assertThat(STAGE.titles(LeakRule.ProseClass.PROSE_COLLIDING)).isNotEmpty();
    }

    @Test
    void theMechanicalRuleIsPinnedAtBothEnds() {
        // Non-prose: an internal capital, a digit or a "+".
        assertThat(OutputStage.classify("ReadyReckoner")).isEqualTo(LeakRule.ProseClass.NON_PROSE);
        assertThat(OutputStage.classify("1500+Rules")).isEqualTo(LeakRule.ProseClass.NON_PROSE);
        assertThat(OutputStage.classify("MarriageMatching")).isEqualTo(LeakRule.ProseClass.NON_PROSE);
        assertThat(OutputStage.classify("249Mindsets")).isEqualTo(LeakRule.ProseClass.NON_PROSE);

        // Prose-colliding: ordinary English or KP vocabulary.
        assertThat(OutputStage.classify("Relationship")).isEqualTo(LeakRule.ProseClass.PROSE_COLLIDING);
        assertThat(OutputStage.classify("Death")).isEqualTo(LeakRule.ProseClass.PROSE_COLLIDING);
        assertThat(OutputStage.classify("Medical Astrology")).isEqualTo(LeakRule.ProseClass.PROSE_COLLIDING);
        assertThat(OutputStage.classify("Vimshottari")).isEqualTo(LeakRule.ProseClass.PROSE_COLLIDING);
    }

    @Test
    void sheetIdsAreClassifiedByTheSamePrinciple() {
        // 16 of the 75 ids are single ordinary English words, so keying every id
        // bare would block ordinary English — the same mistake as keying every
        // title bare, in the other direction.
        assertThat(OutputStage.classifyId("1500-rules")).isEqualTo(LeakRule.ProseClass.NON_PROSE);
        assertThat(OutputStage.classifyId("ready-reckoner")).isEqualTo(LeakRule.ProseClass.NON_PROSE);
        assertThat(OutputStage.classifyId("horary-249-mindsets")).isEqualTo(LeakRule.ProseClass.NON_PROSE);

        assertThat(OutputStage.classifyId("numerology")).isEqualTo(LeakRule.ProseClass.PROSE_COLLIDING);
        assertThat(OutputStage.classifyId("death")).isEqualTo(LeakRule.ProseClass.PROSE_COLLIDING);
        assertThat(OutputStage.classifyId("relationship")).isEqualTo(LeakRule.ProseClass.PROSE_COLLIDING);
    }

    @Test
    void everyOverrideNamesARealSheetTitle() {
        // data-model "Validation at load": a renamed sheet must not leave a stale
        // override behind, silently un-classifying a title.
        Set<String> titles = new HashSet<>();
        TOOLKIT.sheets().forEach(sheet -> titles.add(sheet.title()));

        assertThat(OutputStage.overrides().keySet())
                .as("a stale override would silently stop applying")
                .allMatch(titles::contains);
    }

    @Test
    void theReviewedOverrideIsHonoured() {
        // ABCD is real KP technique vocabulary, so the mechanical rule's NON_PROSE
        // verdict is overridden to PROSE_COLLIDING in the reviewed table.
        assertThat(OutputStage.classify("ABCD Significance")).isEqualTo(LeakRule.ProseClass.NON_PROSE);
        assertThat(STAGE.titles(LeakRule.ProseClass.PROSE_COLLIDING)).contains("ABCD Significance");
    }
}
