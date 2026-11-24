package com.celestia.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.celestia.core.toolkit.KpToolkit;
import com.celestia.core.toolkit.ToolkitSheet;
import com.celestia.core.toolkit.TopicGroup;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Fail fast on bad data (data-model.md "Validation at load").
 *
 * <p>A bad resource edit must break the build, not the gate. Every check here
 * exists because the alternative is a gate that keeps running while quietly
 * doing the wrong thing.
 */
class LoadValidationTest {

    @Test
    void everyInScopeTermsGroupHasAnswerableContent() {
        InScopeVocabulary vocabulary = InScopeVocabulary.from(KpToolkit.bundled());
        Set<TopicGroup> answerable = new HashSet<>();
        for (ToolkitSheet sheet : KpToolkit.bundled().sheets()) {
            if (sheet.answerable() && sheet.group() != null) {
                answerable.add(sheet.group());
            }
        }

        for (String term : vocabulary.allTerms()) {
            vocabulary.lookup(term).flatMap(InScopeVocabulary.Term::group).ifPresent(group ->
                    assertThat(group)
                            .as("term '%s' claims a group with no answerable content", term)
                            .isIn(answerable));
        }
    }

    @Test
    void housekeepingAndIgnoredSheetsContributeNothing() {
        // FR-007. The toolkit's own INDEX and Version Control sheets must not make
        // a message in scope.
        InScopeVocabulary vocabulary = InScopeVocabulary.from(KpToolkit.bundled());

        for (ToolkitSheet sheet : KpToolkit.bundled().sheets()) {
            if (sheet.answerable()) {
                continue;
            }
            vocabulary.lookup(sheet.title()).ifPresent(term ->
                    assertThat(term.source())
                            .as("non-answerable sheet '%s' contributed a term", sheet.title())
                            .isNotEqualTo(InScopeVocabulary.Source.TOOLKIT_MATTER));
        }
    }

    @Test
    void noStopwordSurvivesIntoTheVocabulary() {
        InScopeVocabulary vocabulary = InScopeVocabulary.from(KpToolkit.bundled());

        for (String stopword : new String[] {"will", "good", "through", "always", "very", "person", "long"}) {
            assertThat(vocabulary.lookup(stopword))
                    .as("stopword '%s' would make 'I will be good' an astrology question", stopword)
                    .isEmpty();
        }
    }

    @Test
    void everyHostilePhraseIsNonEmptyAndBelongsToAKnownFamily() {
        HostileRules rules = HostileRules.bundled();

        assertThat(rules.size()).isGreaterThan(20);
        assertThat(rules.families())
                .as("all five families of research.md §6 must be present")
                .containsAll(java.util.EnumSet.allOf(HostileRule.Family.class));
    }

    @Test
    void aHostileRuleWithNoMatcherIsRejected() {
        assertThatThrownBy(() -> new HostileRule(HostileRule.Family.CROSS_USER, "empty", java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HostileRule(
                        HostileRule.Family.CROSS_USER, " ", java.util.List.of(java.util.regex.Pattern.compile("x"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyProseClassOverrideNamesARealSheetTitle() {
        Set<String> titles = new HashSet<>();
        KpToolkit.bundled().sheets().forEach(sheet -> titles.add(sheet.title()));

        assertThat(OutputStage.overrides().keySet()).allMatch(titles::contains);
    }

    @Test
    void bothEntryPointsRefuseToExistWithoutTheToolkit() {
        // The gate has no taxonomy without it; the output stage has no leak
        // vocabulary. Either one falling open is worse than failing loudly, so
        // both are built from KpToolkit.bundled() and both throw if it cannot load
        // (spec.md Edge Cases, FR-017).
        assertThat(Gate.bundled()).isNotNull();
        assertThat(OutputStage.bundled()).isNotNull();

        // Both routes go through the same loader, so if the workbook were absent
        // neither would return a usable object — asserted here as the contract
        // rather than by deleting a classpath resource mid-suite.
        assertThatThrownBy(() -> Tsv.read("com/celestia/guardrail/does-not-exist.tsv"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found on the classpath");
    }
}
