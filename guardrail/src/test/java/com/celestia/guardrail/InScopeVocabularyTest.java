package com.celestia.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.toolkit.KpToolkit;
import org.junit.jupiter.api.Test;

/** The derived in-scope vocabulary (FR-006, FR-007, research.md §4, §12). */
class InScopeVocabularyTest {

    private static final InScopeVocabulary VOCABULARY = InScopeVocabulary.from(KpToolkit.bundled());

    @Test
    void termsAreDerivedFromTheRealBundledToolkit() {
        // FR-006: widening the toolkit widens the gate, with no list to maintain.
        assertThat(VOCABULARY.size()).isGreaterThan(1_000);
        assertThat(VOCABULARY.lookup("marriage")).isPresent();
        assertThat(VOCABULARY.lookup("marriage").orElseThrow().source())
                .isEqualTo(InScopeVocabulary.Source.TOOLKIT_MATTER);
    }

    @Test
    void theEnginesOwnConstantsAreKpVocabulary() {
        for (String term : new String[] {"rahu", "saturn", "aries", "ashwini", "dasha", "horary", "sub lord"}) {
            assertThat(VOCABULARY.lookup(term)).as("term '%s'", term).isPresent();
            assertThat(VOCABULARY.lookup(term).orElseThrow().source())
                    .as("term '%s'", term)
                    .isEqualTo(InScopeVocabulary.Source.KP_VOCABULARY);
        }
    }

    @Test
    void masterRuleBookTermsAllowAMessageWithoutClaimingASubject() {
        // The master rule book holds 1,598 of 2,001 rules and spans every subject,
        // so its vocabulary carries no subject claim (research.md §12 finding 1).
        var invest = VOCABULARY.lookup("invest");

        assertThat(invest).isPresent();
        assertThat(invest.orElseThrow().group())
                .as("a term from a sheet that spans every subject must not claim one")
                .isEmpty();
    }

    @Test
    void theSupplementResolvesTheKnownFalseRefusal() {
        // research.md §12 finding 3: the sheet writes "gems", a user writes
        // "gemstone".
        var gemstone = VOCABULARY.lookup("gemstone");

        assertThat(gemstone).isPresent();
        assertThat(gemstone.orElseThrow().source()).isEqualTo(InScopeVocabulary.Source.SUPPLEMENT);
    }

    @Test
    void stopwordsAreAbsent() {
        for (String stopword : new String[] {"gains", "good", "through", "will", "always", "very", "person", "long"}) {
            assertThat(VOCABULARY.lookup(stopword)).as("stopword '%s'", stopword).isEmpty();
        }
    }

    @Test
    void matchingIsOnWordBoundariesNeverBareSubstrings() {
        // §12 finding 4 — the whole reason matching tokenises rather than searching.
        // ("ank" inside "bank" was the original Hindi illustration; with Hindi
        // deferred, "bank" is itself a real toolkit term, so the property is shown
        // the other way round: a term must not match inside a longer word.)
        assertThat(VOCABULARY.lookup("bank")).as("'bank' is a real toolkit term").isPresent();
        assertThat(VOCABULARY.match("xbankx")).as("a term must not match inside a longer word").isEmpty();
        assertThat(VOCABULARY.match("embankments")).isEmpty();
        assertThat(VOCABULARY.match("xxprogressxx")).isEmpty();

        // But the words themselves, standing alone, are evidence.
        assertThat(VOCABULARY.match("bank")).isPresent();
        assertThat(VOCABULARY.match("what does my chart say")).isPresent();
    }

    @Test
    void oneMatchedTermIsEnough() {
        assertThat(VOCABULARY.match("marriage")).isPresent();
        assertThat(VOCABULARY.match("naukri")).as("Hindi is deferred to post-launch").isEmpty();
    }
}
