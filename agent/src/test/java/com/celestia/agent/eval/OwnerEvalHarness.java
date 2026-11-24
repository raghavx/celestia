package com.celestia.agent.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.celestia.agent.AgentMessages;
import com.celestia.agent.channel.ReplyHandler;
import com.celestia.agent.entitlement.EntitlementDecision;
import com.celestia.agent.entitlement.EntitlementService;
import com.celestia.agent.llm.LlmProperties;
import com.celestia.agent.persistence.entity.OwnerTurnRecordEntity;
import com.celestia.agent.persistence.repository.OwnerTurnRecordRepository;
import com.celestia.agent.persistence.store.AppUserRef;
import com.celestia.agent.persistence.store.BirthDataStore;
import com.celestia.agent.persistence.store.UserAccountService;
import com.celestia.agent.TestcontainersConfiguration;
import com.celestia.channel.whatsapp.InboundMessage;
import com.celestia.channel.whatsapp.MessageContent;
import com.celestia.channel.whatsapp.OutboundMessage;
import com.celestia.core.toolkit.KpToolkit;
import com.celestia.core.toolkit.ToolkitSheet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * SPEC-012 FR-021, US6 — ten of the owner's own questions, run on demand, each
 * answer written out with the toolkit sources behind it so a practitioner can
 * mark it without reading code.
 *
 * <p><b>This is a document generator, not an assertion.</b> What can be checked
 * mechanically is checked here — every answer grounded, nothing withheld, no
 * source named, every reference resolvable, both SC-007 directions counted. What
 * it cannot check is whether the astrology is <i>good</i>, which is why the output
 * is ten files and SC-001's bar is a human verdict (T041).
 *
 * <p><b>Not in the default build.</b> It needs a reachable model, so it carries no
 * {@code Test} or {@code IT} suffix and neither surefire nor failsafe picks it up.
 * Run it deliberately:
 *
 * <pre>{@code ./mvnw -pl agent -am test -Dtest=OwnerEvalHarness}</pre>
 *
 * <p><b>It refuses to run on a bad environment rather than writing useless
 * output.</b> If the owner is not allow-listed, every question returns "not open
 * to you yet"; if his birth data is not seeded, every one returns what is missing.
 * Either way ten documents appear and none contains any astrology, wasting a
 * practitioner's time — so both are checked first and the run fails loudly.
 *
 * <p><b>Which tier this is.</b> Run against the dev machine's small model it proves
 * the round trip and nothing about answer quality. SC-001's verdict and SC-008's
 * latency mean something only against the production VM and Llama 4 Scout — a
 * result from the wrong tier is worse than no result (plan.md "Testing").
 */
@Tag("eval")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OwnerEvalHarness {

    private static final Path QUESTIONS = Path.of(
            "src", "test", "resources", "com", "celestia", "agent", "eval", "owner-questions.tsv");

    private static final Path OUTPUT = Path.of("target", "owner-eval");

    @Autowired
    ReplyHandler handler;

    @Autowired
    EntitlementService entitlement;

    @Autowired
    UserAccountService users;

    @Autowired
    BirthDataStore births;

    @Autowired
    OwnerTurnRecordRepository turnRecords;

    @Autowired
    LlmProperties llm;

    @Value("${celestia.eval.owner-phone:${celestia.seed-owner.phone-e164:}}")
    String ownerPhone;

    private static List<EvalQuestion> questions;

    @BeforeAll
    static void readQuestions() {
        questions = EvalQuestion.read(QUESTIONS);
    }

    /** One question with the behaviour it is there to test. */
    record EvalQuestion(String id, String question, String expectation, String note) {

        boolean answerable() {
            return "ANSWERABLE".equals(expectation);
        }

        static List<EvalQuestion> read(Path path) {
            assertThat(path).as("the evaluation set must be where the harness expects it").exists();
            List<EvalQuestion> all = new ArrayList<>();
            try {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    if (line.isBlank() || line.startsWith("#") || line.startsWith("id\t")) {
                        continue;
                    }
                    String[] columns = line.split("\t", -1);
                    all.add(new EvalQuestion(columns[0], columns[1], columns[2], columns[3]));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return all;
        }
    }

    /** What one question produced, so the assertions can read it back. */
    private record Produced(EvalQuestion question, String reply, OwnerTurnRecordEntity record) {}

    @Test
    @DisplayName("ten answers with their resolved sources, for a practitioner to mark (SC-001)")
    void produceTheEvaluationSet() throws IOException {
        requireAnEnvironmentWorthRunningIn();

        assertThat(questions)
                .as("FR-021 requires at least ten of the owner's own questions")
                .hasSizeGreaterThanOrEqualTo(10);

        Files.createDirectories(OUTPUT);
        AppUserRef owner = users.findOrCreate(ownerPhone);
        List<Produced> produced = new ArrayList<>();

        for (EvalQuestion question : questions) {
            OutboundMessage reply = handler.reply(owner, inbound(question));
            String body = ((OutboundMessage.TextMessage) reply).body();
            OwnerTurnRecordEntity record = turnRecords.findByUserIdOrderByReceivedAtAsc(owner.id()).stream()
                    .reduce((first, last) -> last)
                    .orElseThrow(() -> new AssertionError(
                            "no turn record for " + question.id() + " — a turn that ran must be reviewable"));
            produced.add(new Produced(question, body, record));
            Files.writeString(OUTPUT.resolve(question.id() + ".md"), document(question, body, record),
                    StandardCharsets.UTF_8);
        }

        assertMechanicalCriteria(produced);
        System.out.println("Ten documents written to " + OUTPUT.toAbsolutePath()
                + " — read them and mark each answer acceptable or not AS ASTROLOGY (SC-001, bar 9 of 10).");
    }

    /**
     * The criteria a machine can judge (T035). Everything here is a defect if it
     * fails, and none of it is a judgement about the astrology.
     */
    private void assertMechanicalCriteria(List<Produced> produced) {
        List<String> ungrounded = new ArrayList<>();
        List<String> withheld = new ArrayList<>();
        List<String> namedASource = new ArrayList<>();
        List<String> falseDeclines = new ArrayList<>();
        List<String> falseAnswers = new ArrayList<>();
        List<String> unresolvedReferences = new ArrayList<>();

        List<String> sheetIds = KpToolkit.bundled().sheets().stream()
                .map(ToolkitSheet::id)
                .filter(id -> id.contains("-"))
                .toList();

        for (Produced row : produced) {
            String outcome = row.record().getOutcome();
            boolean answered = "ANSWERED".equals(outcome);

            // SC-002: every reply asserting KP judgement rests on a ToolkitTools
            // call. An engine call carries no source and grounds nothing.
            if (answered && !row.record().getToolCallsJson().contains("findToolkitRules")
                    && !row.record().getToolCallsJson().contains("getChartPromises")
                    && !row.record().getToolCallsJson().contains("lookupToolkit")
                    && !row.record().getToolCallsJson().contains("getToolkitLookup")
                    && !row.record().getToolCallsJson().contains("getHoraryMindset")) {
                ungrounded.add(row.question().id());
            }
            // SC-004: a withheld reply is a lost answer and a prompt defect.
            if ("WITHHELD".equals(outcome)) {
                withheld.add(row.question().id());
            }
            // SC-003: no delivered reply names the toolkit, a sheet or a row.
            String lowered = row.reply().toLowerCase(Locale.ROOT);
            for (String id : sheetIds) {
                if (lowered.contains(id)) {
                    namedASource.add(row.question().id() + " named " + id);
                }
            }
            if (lowered.contains("sheet") || lowered.contains("workbook") || lowered.contains("row ")) {
                namedASource.add(row.question().id() + " used the vocabulary of a document");
            }
            // SC-011: every reference resolves to a real source.
            if (row.record().getToolCallsJson().contains("\"s1\"")
                    && !row.record().getResolvedSourcesJson().contains("s1")) {
                unresolvedReferences.add(row.question().id());
            }
            // SC-007: both directions, counted apart.
            if (row.question().answerable() && !answered) {
                falseDeclines.add(row.question().id() + " (" + outcome + ")");
            }
            if (!row.question().answerable() && answered) {
                falseAnswers.add(row.question().id());
            }
        }

        assertThat(withheld).as("SC-004 — withheld replies (a lost answer and a prompt defect)").isEmpty();
        assertThat(namedASource).as("SC-003 — replies naming a source").isEmpty();
        assertThat(ungrounded).as("SC-002 — answers with no toolkit call behind them").isEmpty();
        assertThat(unresolvedReferences).as("SC-011 — references that resolve to nothing").isEmpty();
        assertThat(falseDeclines).as("SC-007 — answerable questions that were declined").isEmpty();
        assertThat(falseAnswers).as("SC-007 — no-basis questions that were answered anyway").isEmpty();
    }

    /**
     * Refuses to run rather than writing ten documents a practitioner cannot use.
     */
    private void requireAnEnvironmentWorthRunningIn() {
        assertThat(ownerPhone)
                .as("set celestia.eval.owner-phone (or celestia.seed-owner.phone-e164) to the owner's number")
                .isNotBlank();
        assertThat(entitlement.check(ownerPhone))
                .as("the owner must be on celestia.entitlement.allow-list, or every question "
                        + "returns \"%s\" and no document contains any astrology",
                        AgentMessages.NOT_OPEN_YET)
                .isEqualTo(EntitlementDecision.OWNER_ALLOW_LIST);

        AppUserRef owner = users.findOrCreate(ownerPhone);
        assertThat(births.load(owner.id()))
                .as("the owner's birth data must be seeded (T030), or every question returns \"%s\"",
                        AgentMessages.BIRTH_DETAILS_MISSING)
                .isPresent();

        System.out.println("Running the evaluation set against " + llm.baseUrl() + " model " + llm.model()
                + ". If that is a small local model, this proves the round trip and NOTHING about "
                + "answer quality — do not put its output in front of a practitioner (ADR-0009).");
    }

    private static InboundMessage inbound(EvalQuestion question) {
        return new InboundMessage(
                "eval." + question.id(), System.getProperty("celestia.eval.phone", ""),
                Instant.now(), new MessageContent.TextContent(question.question()));
    }

    /** One reviewable document: the question, the reply, and the basis behind it. */
    private String document(EvalQuestion question, String reply, OwnerTurnRecordEntity record) {
        return """
                # %s

                **Question** (as the owner typed it)

                > %s

                **Expectation**: %s — %s

                ## The reply he received

                %s

                ## Your verdict

                - [ ] Acceptable as astrology
                - [ ] Not acceptable

                Notes:

                Two failure modes are worth watching for specifically: a confident answer
                resting on a thin basis, and a decline that should have been an answer.

                ## How it was produced (for review, not for the owner)

                | | |
                |---|---|
                | outcome | `%s` |
                | model | `%s`%s |
                | reply length | %d characters%s |
                | advisory class | `%s` |

                ### Tool calls, in order

                ```json
                %s
                ```

                ### The sources behind the references

                ```json
                %s
                ```
                """
                .formatted(
                        question.id(),
                        question.question(),
                        question.expectation(),
                        question.note(),
                        reply,
                        record.getOutcome(),
                        String.valueOf(record.getModelUsed()),
                        record.isFallbackSwitched() ? " (switched to the fallback)" : "",
                        record.getDeliveredLength(),
                        record.isReplyTrimmed() ? " — TRIMMED to the channel budget, a prompt defect" : "",
                        record.getSensitivityUsed(),
                        record.getToolCallsJson(),
                        record.getResolvedSourcesJson());
    }
}
