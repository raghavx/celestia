package com.celestia.core.toolkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * T040 — nothing was silently dropped, and nothing forbidden leaks out
 * (SPEC-010 FR-001/010/014, SC-001/002).
 *
 * <p>"The toolkit as the base" only means something if the stored copy is
 * complete and self-consistent. A sheet that went missing between the manifest
 * and the properties file would otherwise leave the product answering from part
 * of the toolkit while believing it held all of it.
 *
 * <p>The first group of checks runs against a synthetic fixture, so they hold on
 * any checkout. The group at the end asserts the real 75/75 sheets and 2,000+
 * rules (SC-001, SC-002) against the imported workbook, and skips if the toolkit
 * has not been imported.
 */
class ToolkitCompletenessTest {

    private static KpToolkit toolkit;

    @BeforeAll
    static void loadFixture() {
        toolkit = KpToolkit.load(SheetFixtureSupport.write());
    }

    @Test
    void theManifestThePropertiesAndTheLoadedSheetsAllAgree() {
        assertThat(toolkit.sheets()).hasSize(toolkit.version().sheets());
        assertThat(toolkit.promiseRules()).hasSize(toolkit.version().rules());
    }

    @Test
    void everySheetIsStoredCompleteWithItsRows() {
        for (ToolkitSheet sheet : toolkit.sheets()) {
            assertThat(sheet.rowCount()).as("%s has rows", sheet.id()).isPositive();
            assertThat(sheet.title()).isNotBlank();
            assertThat(sheet.id()).isNotBlank();
        }
    }

    @Test
    void sheetIdsAreUniqueAndOrdersAreContiguous() {
        List<String> ids = toolkit.sheets().stream().map(ToolkitSheet::id).toList();
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(toolkit.sheets().stream().map(ToolkitSheet::order).toList())
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, ids.size()).boxed().toList());
    }

    @Test
    void everyRuleNamesASheetTheManifestKnows() {
        for (PromiseRule rule : toolkit.promiseRules()) {
            assertThat(toolkit.sheet(rule.source()))
                    .as("rule %s names source sheet %s", rule.id(), rule.source())
                    .isPresent();
        }
    }

    @Test
    void ruleIdsAreUnique() {
        assertThat(toolkit.promiseRules().stream().map(PromiseRule::id).toList())
                .doesNotHaveDuplicates();
    }

    @Test
    void rulesAreReachableByTheHouseTheyJudge() {
        for (PromiseRule rule : toolkit.promiseRules()) {
            assertThat(toolkit.rulesFor(rule.judgedHouse()))
                    .as("rule %s is listed under house %d", rule.id(), rule.judgedHouse())
                    .contains(rule);
        }
    }

    @Test
    void nonAnswerableSheetsNeverSurfaceInSearch() {
        // FR-014: housekeeping and author-"ignore" sheets are stored and
        // browsable by id, but are never answer content.
        ToolkitSheet housekeeping = toolkit.sheet("fixture-index").orElseThrow();
        assertThat(housekeeping.answerable()).isFalse();

        assertThat(toolkit.search("INDEX", 10)).noneMatch(hit -> hit.sheet().id().equals("fixture-index"));
        assertThat(toolkit.search("fixture", 20)).allMatch(hit -> hit.sheet().answerable());
    }

    @Test
    void theSensitiveAndBeyondNatalKpGroupsAreAllAnswerable() {
        // FR-010 names these explicitly: in scope, with the fixed advisory
        // sentence rather than a refusal.
        for (TopicGroup group : SheetFixtureSupport.ANSWERABLE_GROUPS) {
            List<ToolkitSheet> sheets = toolkit.sheets(group);
            assertThat(sheets).as("%s has at least one sheet", group).isNotEmpty();
            assertThat(sheets)
                    .as("every %s sheet is answerable", group)
                    .allMatch(ToolkitSheet::answerable);
        }
    }

    @Test
    void findRulesMatchesOnMatterAndCategory() {
        assertThat(toolkit.findRules("settled fixture")).isNotEmpty();
        assertThat(toolkit.findRules("no such matter anywhere")).isEmpty();
        assertThat(toolkit.findRules("")).isEmpty();
        assertThat(toolkit.findRules(null)).isEmpty();
    }

    // ---------------------------------------------------- the real toolkit

    @Test
    @EnabledIf("com.celestia.core.toolkit.RealToolkit#isImported")
    void theRealToolkitHoldsEverySheetOfTheWorkbook() {
        // SC-001: 75 of 75 sheets stored, every row present.
        KpToolkit real = KpToolkit.bundled();
        assertThat(real.sheets()).hasSize(75);
        assertThat(real.sheets()).allSatisfy(sheet ->
                assertThat(sheet.rowCount()).as("%s has rows", sheet.id()).isPositive());
        assertThat(real.version().sheets()).isEqualTo(75);
        assertThat(real.version().version()).isEqualTo("2026-09-12");
    }

    @Test
    @EnabledIf("com.celestia.core.toolkit.RealToolkit#isImported")
    void theRealToolkitYieldsAtLeastTwoThousandStructuredRules() {
        // SC-002: >= 2,000 house-formula rules, each traceable to its sheet.
        KpToolkit real = KpToolkit.bundled();
        assertThat(real.promiseRules()).hasSizeGreaterThanOrEqualTo(2_000);
        assertThat(real.promiseRules()).allSatisfy(rule -> {
            assertThat(real.sheet(rule.source())).as("source of %s", rule.id()).isPresent();
            assertThat(rule.matter()).isNotBlank();
        });
    }

    @Test
    @EnabledIf("com.celestia.core.toolkit.RealToolkit#isImported")
    void theRealToolkitKeepsHousekeepingAndIgnoredSheetsOutOfAnswers() {
        // FR-014: the author's own "Under research - Ignore" sheet included.
        KpToolkit real = KpToolkit.bundled();
        for (String id : List.of("index", "version-control", "software-list",
                "planets-stock-sectors", "zodiac-places")) {
            assertThat(real.sheet(id)).as("%s is stored", id).isPresent();
            assertThat(real.sheet(id).orElseThrow().answerable())
                    .as("%s is not answer content", id)
                    .isFalse();
        }
        assertThat(real.search("under research ignore", 20))
                .noneMatch(hit -> hit.sheet().id().equals("planets-stock-sectors"));
    }

    @Test
    void anImpossibleHouseIsRejectedRatherThanReturningNothing() {
        assertThat(toolkit.rulesFor(7)).isNotNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> toolkit.rulesFor(0))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> toolkit.rulesFor(13))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
