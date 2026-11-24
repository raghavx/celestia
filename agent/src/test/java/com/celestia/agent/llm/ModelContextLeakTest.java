package com.celestia.agent.llm;

import static com.celestia.agent.llm.StubChatModel.toolCall;
import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.core.toolkit.KpToolkit;
import com.celestia.core.toolkit.ToolkitSheet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SPEC-012 SC-010 and FR-018, <b>asserted on what the model was given</b> rather
 * than on what it replied.
 *
 * <p>This is the mechanical half of Constitution X and the reason the reference
 * swap exists. A reply can be checked for a leak after the fact — SPEC-011's scan
 * does that, and catching one costs the whole answer. Checking the request proves
 * the model never had the string to leak.
 */
class ModelContextLeakTest {

    /** A turn that reaches every one of SPEC-010's five tools. */
    private static AnswererHarness turnUsingEveryToolkitTool() {
        AnswererHarness harness = AnswererHarness.withRoundCap(10);
        harness.model
                .thenCallTools(
                        toolCall("getChartPromises", "{\"house\":7,\"includePartial\":true}"),
                        toolCall("findToolkitRules", "{\"matter\":\"marriage\"}"))
                .thenCallTools(
                        toolCall("lookupToolkit", "{\"topic\":\"marriage matching\"}"),
                        toolCall("getToolkitLookup", "{\"point\":\"SATURN\",\"table\":\"disease\"}"),
                        toolCall("getHoraryMindset", "{\"number\":42}"))
                .thenAnswer("The 7th cusp sub lord connects 2, 7 and 11; the period matures in 2027.");
        return harness;
    }

    @Test
    @DisplayName("no source the turn actually used reaches the model (SC-010)")
    void noToolkitSourceEntersTheModelsContext() {
        AnswererHarness harness = turnUsingEveryToolkitTool();
        AnswererHarness.Outcome outcome = harness.answer("when will I get married?");
        String seen = harness.model.everythingSeen();

        Map<String, SourceRef> used = outcome.scope().references().resolved();
        assertThat(used)
                .as("the turn must actually have used sources, or this test proves nothing")
                .isNotEmpty();

        for (Map.Entry<String, SourceRef> entry : used.entrySet()) {
            SourceRef source = entry.getValue();
            assertThat(seen)
                    .as("sheet id %s behind reference %s", source.sheetId(), entry.getKey())
                    .doesNotContain(source.sheetId());
            assertThat(seen)
                    .as("sheet title %s behind reference %s", source.sheetTitle(), entry.getKey())
                    .doesNotContain(source.sheetTitle());
        }
    }

    @Test
    @DisplayName("no sheet id from the whole toolkit appears, used by this turn or not")
    void noSheetIdAtAll() {
        // Only the hyphenated ids — marriage-rules, star-wise-diseases,
        // planet-position-signification — can be searched for without false
        // positives. Some ids are ordinary words (profession, death, finance,
        // luck), which appear legitimately in a tool description or in toolkit
        // content, and the single-word ones are covered where it actually matters:
        // noToolkitSourceEntersTheModelsContext checks EVERY source the turn used,
        // whatever its id looks like, and that turn used finance, luck and romance.
        AnswererHarness harness = turnUsingEveryToolkitTool();
        harness.answer("when will I get married?");
        String seen = harness.model.everythingSeen();

        List<String> distinctiveIds = KpToolkit.bundled().sheets().stream()
                .map(ToolkitSheet::id)
                .filter(id -> id.contains("-"))
                .toList();
        assertThat(distinctiveIds).hasSizeGreaterThan(30);
        for (String id : distinctiveIds) {
            assertThat(seen).as("sheet id %s", id).doesNotContain(id);
        }
    }

    @Test
    @DisplayName("the structure itself is absent: no sheetId, sheetTitle or row field")
    void noSourceFieldsAreSerialisedAtAll() {
        // Row numbers cannot be searched for as digits — "7" is a house, a pada and
        // a horary number. What can be asserted is that no result carries a row
        // field for a reader to find a row number in.
        AnswererHarness harness = turnUsingEveryToolkitTool();
        harness.answer("when will I get married?");
        String seen = harness.model.everythingSeen();

        assertThat(seen).doesNotContain("sheetId", "sheetTitle", "\"row\"", "sheet_id", "sheet_title");
    }

    @Test
    @DisplayName("the references the model does see are the opaque ones")
    void theModelSeesReferencesInstead() {
        AnswererHarness harness = turnUsingEveryToolkitTool();
        harness.answer("when will I get married?");
        assertThat(harness.model.everythingSeen())
                .as("the swap must actually have put references in front of the model")
                .contains("\"references\":[\"s1\"]")
                .contains("\"reference\":\"s");
    }

    @Test
    @DisplayName("a second turn carries no trace of the first (FR-018)")
    void turnsAreStateless() {
        // The claim at the centre of the stateless decision: nothing of a previous
        // message reaches the model. A seventh story asserted this until Q2 was
        // revised to stateless and the story went with it.
        AnswererHarness harness = AnswererHarness.standard();
        harness.model.thenAnswer("Her 7th house is strong.").thenAnswer("Your 10th cusp sub lord rules work.");

        String firstQuestion = "will my sister Priya marry a doctor in Bengaluru?";
        harness.answer(firstQuestion);
        int afterFirst = harness.model.callCount();
        harness.answer("and what about my career?");

        String secondTurnOnly = harness.model.promptsSeen().stream()
                .skip(afterFirst)
                .flatMap(prompt -> prompt.getInstructions().stream())
                .map(message -> String.valueOf(message.getText()))
                .reduce("", (a, b) -> a + "\n" + b);

        assertThat(secondTurnOnly).contains("and what about my career?");
        for (String fragment : List.of("Priya", "sister", "Bengaluru", "doctor", firstQuestion)) {
            assertThat(secondTurnOnly).as("fragment %s of the first message", fragment).doesNotContain(fragment);
        }
        assertThat(secondTurnOnly)
                .as("nor the previous answer")
                .doesNotContain("Her 7th house is strong");
    }

    @Test
    @DisplayName("each turn starts its references at s1, so no durable map accumulates")
    void referencesRestartEachTurn() {
        AnswererHarness harness = AnswererHarness.standard();
        harness.model
                .thenCallTools(toolCall("findToolkitRules", "{\"matter\":\"marriage\"}"))
                .thenAnswer("first")
                .thenCallTools(toolCall("findToolkitRules", "{\"matter\":\"career\"}"))
                .thenAnswer("second");

        AnswererHarness.Outcome first = harness.answer("when will I get married?");
        AnswererHarness.Outcome second = harness.answer("what about my career?");

        assertThat(first.scope().references().references()).startsWith("s1");
        assertThat(second.scope().references().references()).startsWith("s1");
        assertThat(second.scope().references().resolve("s1"))
                .isNotEqualTo(first.scope().references().resolve("s1"));
    }
}
