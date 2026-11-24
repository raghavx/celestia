package com.celestia.agent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SPEC-012 FR-013a–c: the reference is an index, never an encoding.
 *
 * <p>This is the narrower half of Constitution X's mechanical control — that the
 * swap itself behaves. That no toolkit string reaches the model is asserted on
 * what the model received, in {@link ModelContextLeakTest} (SC-010).
 */
class SourceReferencesTest {

    private static final SourceRef MARRIAGE = new SourceRef("marriage-rules", "MarriageRules", 7);
    private static final SourceRef FINANCE = new SourceRef("finance", "Finance Astrology", 12);

    @Test
    @DisplayName("the same source twice in a turn gets the same reference")
    void stableWithinATurn() {
        // SPEC-010 agent-tools.md obligation 3 — two findings resting on the same
        // rule must still be recognisable as agreeing — has to survive the swap.
        SourceReferences refs = new SourceReferences();
        assertThat(refs.reference(MARRIAGE)).isEqualTo(refs.reference(MARRIAGE));
        assertThat(refs.size()).isEqualTo(1);
    }

    @Test
    void differentSourcesGetDifferentReferences() {
        SourceReferences refs = new SourceReferences();
        assertThat(refs.reference(MARRIAGE)).isNotEqualTo(refs.reference(FINANCE));
        assertThat(refs.references()).containsExactly("s1", "s2");
    }

    @Test
    @DisplayName("a reference cannot be reversed to its source")
    void referencesCarryNothingOfTheSource() {
        // A hash, a slug or a base64 would put the sheet id back in the model's
        // context by another route (FR-013c).
        SourceReferences refs = new SourceReferences();
        String reference = refs.reference(MARRIAGE);
        assertThat(reference).isEqualTo("s1");
        assertThat(reference).doesNotContain("marriage", "Marriage", "rules", "7");
        for (String token : new String[] {"marriage-rules", "MarriageRules", "marriage", "rules"}) {
            assertThat(reference.toLowerCase()).doesNotContain(token.toLowerCase());
        }
    }

    @Test
    void theRealSourceResolvesForTheRecordAndForReview() {
        SourceReferences refs = new SourceReferences();
        String reference = refs.reference(MARRIAGE);
        assertThat(refs.resolve(reference)).contains(MARRIAGE);
        assertThat(refs.resolved()).containsEntry("s1", MARRIAGE);
    }

    @Test
    void anUnknownReferenceResolvesToNothing() {
        assertThat(new SourceReferences().resolve("s99")).isEmpty();
    }

    @Test
    @DisplayName("references restart per turn, so no durable map of the toolkit accumulates")
    void scopedToOneTurn() {
        SourceReferences first = new SourceReferences();
        first.reference(FINANCE);
        SourceReferences second = new SourceReferences();
        assertThat(second.reference(MARRIAGE)).isEqualTo("s1");
        assertThat(second.resolve("s1")).contains(MARRIAGE);
    }

    @Test
    void aToolCanReportJustTheReferencesItMinted() {
        SourceReferences refs = new SourceReferences();
        refs.reference(MARRIAGE);
        int before = refs.size();
        refs.reference(FINANCE);
        assertThat(refs.referencesSince(before)).containsExactly("s2");
        assertThat(refs.referencesSince(refs.size())).isEmpty();
    }
}
