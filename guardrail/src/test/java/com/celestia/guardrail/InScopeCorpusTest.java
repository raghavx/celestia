package com.celestia.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.toolkit.KpToolkit;
import com.celestia.core.toolkit.ToolkitSheet;
import com.celestia.core.toolkit.TopicGroup;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The in-scope corpus as a standing CI gate (FR-019, SC-002).
 *
 * <p>Every case must be <b>allowed</b>, and <b>nothing is asserted about its
 * topic group</b> — two replays against the real toolkit showed that neither
 * "the decision's group equals topicGroup" nor the weaker "some matched term is
 * attributable to it" is satisfiable, because the master rule book holds 1,598
 * of the 2,001 rules and spans every subject. The group is a diagnostic label,
 * not a result (research.md §12 finding 1, §13).
 */
class InScopeCorpusTest {

    private static final String RESOURCE = "com/celestia/guardrail/corpus-in-scope.tsv";

    private static final Gate GATE = Gate.bundled();

    static Stream<CorpusFile.Case> cases() {
        return CorpusFile.read(RESOURCE).stream();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("cases")
    void everyGenuineQuestionIsAllowed(CorpusFile.Case testCase) {
        GateDecision decision = GATE.decide(new GateRequest(testCase.get("message"), "+911111111111"));

        assertThat(decision.isAllowed())
                .as("a real question was refused — under fail-closed that is a user turned away: %s",
                        testCase.get("message"))
                .isTrue();
        assertThat(decision.reason()).isEqualTo(GateDecision.Reason.IN_SCOPE);
        assertThat(decision.matchedRule()).as("the term that admitted it, for the operator").isNotBlank();
    }

    @Test
    void everyAnswerableTopicGroupIsCovered() {
        // The group list is read FROM THE TOOLKIT, not hard-coded, so adding an
        // answerable sheet in a new group fails the build until the corpus covers
        // it (SC-002). At the current release that is 15 groups.
        Set<TopicGroup> answerable = new HashSet<>();
        for (ToolkitSheet sheet : KpToolkit.bundled().sheets()) {
            if (sheet.answerable() && sheet.group() != null) {
                answerable.add(sheet.group());
            }
        }
        Set<String> covered = new HashSet<>();
        CorpusFile.read(RESOURCE).forEach(c -> covered.add(c.get("topicGroup")));

        assertThat(answerable.stream().map(TopicGroup::name).toList())
                .as("each answerable topic group needs at least one English question in the corpus")
                .allMatch(covered::contains);
    }

    @Test
    void theCorpusIsLiveDataNotCode() {
        // SC-009: as many cases execute as the file has rows, so a reviewer adding
        // a line needs no code change.
        assertThat(CorpusFile.dataRows(RESOURCE)).isGreaterThanOrEqualTo(15);
        assertThat(CorpusFile.read(RESOURCE))
                .allSatisfy(c -> assertThat(c.get("message")).as("every row needs a message").isNotBlank());
    }
}
