package com.celestia.agent.channel;

import com.celestia.agent.AgentMessages;
import com.celestia.agent.entitlement.EntitlementDecision;
import com.celestia.agent.entitlement.EntitlementService;
import com.celestia.agent.llm.LlmProperties;
import com.celestia.agent.llm.SystemPrompt;
import com.celestia.agent.llm.ToolNames;
import com.celestia.agent.llm.TurnContext;
import com.celestia.agent.llm.TurnDetail;
import com.celestia.agent.llm.TurnScope;
import com.celestia.agent.persistence.store.AppUserRef;
import com.celestia.agent.persistence.store.BirthDataStore;
import com.celestia.agent.persistence.store.ChartCacheService;
import com.celestia.agent.persistence.store.NatalChartBundle;
import com.celestia.agent.persistence.store.TurnRecordStore;
import com.celestia.channel.whatsapp.InboundMessage;
import com.celestia.channel.whatsapp.MessageContent.TextContent;
import com.celestia.channel.whatsapp.OutboundMessage;
import com.celestia.channel.whatsapp.OutboundMessage.TextMessage;
import com.celestia.core.toolkit.Sensitivity;
import com.celestia.guardrail.Answerer;
import com.celestia.guardrail.FixedMessages;
import com.celestia.guardrail.GateDecision;
import com.celestia.guardrail.GateRequest;
import com.celestia.guardrail.GuardedPipeline;
import com.celestia.guardrail.OutputResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The reply path (SPEC-012 FR-001, FR-005, FR-010a–c), replacing
 * {@code EchoReplyHandler}.
 *
 * <p><b>The order is the requirement, not an implementation detail</b>
 * (data-model.md "Order of operations"):
 *
 * <pre>
 * 1. entitlement    → denied? fixed message. No permit, no gate, no chart, no model.
 * 2. non-text       → fixed message. No permit taken: a "text only" reply must not
 *                     consume the one the owner is waiting for.
 * 3. acquire permit → unavailable? "busy, try again shortly". NO TURN RECORD.
 *    ── a turn starts here (FR-010c) ──
 * 4. pre-fetch      → chart + running dasha (FR-009)
 * 5. gate           → recorded, so an operator can tell a refusal from a fault
 * 6. the pipeline   → gate, answerer, output stage (SPEC-011)
 * 7. clamp          → the channel budget, at a sentence boundary (FR-017)
 * 8. turn record    → written whatever happened from step 3 on, faults included
 * 9. release permit → in a finally, or one fault locks the product permanently
 * </pre>
 *
 * <p><b>Why the permit sits at 3.</b> After entitlement, so a sender who may not
 * be answered cannot take the only permit and lock the owner out — a
 * denial-of-service that costs the attacker nothing. Before the gate and the
 * pre-fetch, so no chart work or gate decision is done for a message that is
 * about to be turned away.
 *
 * <p><b>Why steps 1–3 write no turn record.</b> A turn begins when the permit is
 * acquired (FR-010c), so there is nothing yet to record. They are <b>counted</b>
 * instead — {@code celestia.agent.turned_away} — or the busiest failure mode would
 * be the least visible one.
 */
@Component
public class GuardedReplyHandler implements ReplyHandler {

    private static final Logger log = LoggerFactory.getLogger(GuardedReplyHandler.class);

    /** WhatsApp's own limit for a text body, and the bound SPEC-011 applies inbound. */
    static final int REPLY_BUDGET = 4096;

    private static final String COUNTER = "celestia.agent.turned_away";

    private final EntitlementService entitlement;
    private final Semaphore turnPermit;
    private final ChartCacheService charts;
    private final BirthDataStore births;
    private final GuardedPipeline pipeline;
    private final Answerer answerer;
    private final TurnRecordStore turnRecords;
    private final SystemPrompt systemPrompt;
    private final LlmProperties properties;
    private final Clock clock;

    private final Counter noEntitlement;
    private final Counter notText;
    private final Counter noPermit;

    @SuppressWarnings("java:S107") // the order of operations needs every one of these
    public GuardedReplyHandler(
            EntitlementService entitlement,
            Semaphore turnPermit,
            ChartCacheService charts,
            BirthDataStore births,
            GuardedPipeline pipeline,
            Answerer answerer,
            TurnRecordStore turnRecords,
            SystemPrompt systemPrompt,
            LlmProperties properties,
            Clock clock,
            MeterRegistry meters) {
        this.entitlement = entitlement;
        this.turnPermit = turnPermit;
        this.charts = charts;
        this.births = births;
        this.pipeline = pipeline;
        this.answerer = answerer;
        this.turnRecords = turnRecords;
        this.systemPrompt = systemPrompt;
        this.properties = properties;
        this.clock = clock;
        this.noEntitlement = Counter.builder(COUNTER).tag("reason", "no_entitlement").register(meters);
        this.notText = Counter.builder(COUNTER).tag("reason", "not_text").register(meters);
        this.noPermit = Counter.builder(COUNTER).tag("reason", "no_permit").register(meters);
    }

    @Override
    public OutboundMessage reply(AppUserRef user, InboundMessage inbound) {
        String to = inbound.fromE164();

        // 1. Entitlement, before anything else. Work done for someone not entitled
        //    to it is work that should not have been done.
        EntitlementDecision decision = entitlement.check(to);
        if (!decision.allowed()) {
            noEntitlement.increment();
            log.info("turned away: no entitlement ({})", decision);
            return new TextMessage(to, AgentMessages.NOT_OPEN_YET);
        }

        // 2. Non-text, before the permit. A "text only" reply must not consume the
        //    one permit the owner is waiting for.
        if (!(inbound.content() instanceof TextContent text)) {
            notText.increment();
            return new TextMessage(to, AgentMessages.TEXT_ONLY);
        }

        // 3. The permit. A turn begins here, and not before.
        if (!turnPermit.tryAcquire()) {
            noPermit.increment();
            log.info("turned away: no turn permit free");
            return new TextMessage(to, AgentMessages.BUSY_TRY_AGAIN);
        }
        try {
            return answerTurn(user, inbound, text.body(), to);
        } finally {
            // In a finally, or one fault locks the product permanently.
            turnPermit.release();
        }
    }

    /** Everything from the permit on is recorded, whatever happened. */
    private OutboundMessage answerTurn(AppUserRef user, InboundMessage inbound, String question, String to) {
        TurnDetail.Builder detail = TurnDetail.builder(user.id(), inbound.timestamp(), question)
                .entitlement(TurnDetail.Entitlement.ALLOWED);

        // 4. The chart, before the model is invoked (FR-009). A missing birth record
        //    is reported, never worked around with a default or an assumed chart.
        if (births.load(user.id()).isEmpty()) {
            detail.outcome(TurnDetail.Outcome.AGENT_NO_BASIS).deliveredReply(AgentMessages.BIRTH_DETAILS_MISSING);
            record(detail);
            return new TextMessage(to, AgentMessages.BIRTH_DETAILS_MISSING);
        }
        NatalChartBundle chart = charts.natalChart(user.id());
        TurnContext context = TurnContext.of(user.id(), to, question, chart);

        GateRequest request = new GateRequest(question, to);

        // 5. The gate decision, for the record. `handle` returns only an
        //    OutputResult, so the decision an operator needs is taken here — Gate
        //    is pure and does no I/O, so a second evaluation costs microseconds.
        GateDecision gate = pipeline.gate(request);
        detail.gate(gate.outcome().name(), gate.reason().name());

        TurnScope scope = new TurnScope(
                context, clock.instant(), properties.turnDeadline(), properties.maxToolRounds());
        // Wrapped at the Answerer boundary, because `handle` returns only an
        // OutputResult and the Answer's own sensitivity is what the output stage
        // decorated with. Reading it off the scope instead would be right only as
        // long as the answerer happens to report the same value — and the clamp
        // below needs to know which advisory sentence was appended, or it trims
        // the one thing Constitution V makes obligatory.
        CapturingAnswerer capturing = new CapturingAnswerer(answerer);
        OutputResult result;
        try {
            scope.open();
            // 6. The guardrail's own path: gate, then the answerer only if allowed,
            //    then the output stage. Neither end is bypassed (FR-005).
            result = pipeline.handle(request, answerContext(to), capturing);
        } catch (RuntimeException unexpected) {
            // The answerer never throws for model trouble; anything here is a bug,
            // and the owner still gets a reply rather than silence.
            log.error("unexpected failure in the guarded path", unexpected);
            detail.outcome(TurnDetail.Outcome.FAULT)
                    .faultKind(TurnDetail.FaultKind.MODEL_UNREACHABLE)
                    .deliveredReply(AgentMessages.COULD_NOT_COMPLETE);
            record(detail);
            return new TextMessage(to, AgentMessages.COULD_NOT_COMPLETE);
        } finally {
            scope.close();
        }

        // 7. The channel budget. The output stage only ever appends, so a draft
        //    inside the bound can leave it outside (FR-017, SC-009).
        Sensitivity decoratedWith = capturing.sensitivityUsed().orElse(Sensitivity.NONE);
        Clamped clamped = clamp(result.text(), decoratedWith);

        detail.toolCalls(scope.toolCalls())
                .resolvedSources(scope.references().resolved())
                .sensitivityUsed(decoratedWith)
                .deliveredReply(clamped.text())
                .replyTrimmed(clamped.trimmed())
                .outcome(outcomeOf(gate, result, scope))
                .model(scope.modelUsed().orElse(null), scope.fallbackSwitched());
        scope.fault().ifPresent(detail::faultKind);
        // Optional<?> on purpose: OutputResult.withheldReason() is typed on
        // LeakRule.LeakFamily, which is package-private in guardrail and so cannot
        // be named here. The wildcard hides the inaccessible type and toString()
        // is enough for the record. (guardrail is not ours to widen in this spec.)
        Optional<?> withheld = result.withheldReason();
        withheld.ifPresent(family -> detail.withheldFamily(family.toString()));
        record(detail);

        if (clamped.trimmed()) {
            log.warn("reply trimmed to the channel budget — a prompt defect, not normal operation");
        }
        return new TextMessage(to, clamped.text());
    }

    /**
     * What the output stage needs and only this module knows: the owner's own
     * identifier, so the leak scan can tell it from anyone else's, and the strings
     * most worth scanning for if the model ever echoes them.
     *
     * <p>{@code recipientRef} is the <b>full E.164 number</b>, not a hash or a
     * token: the scan compares its digits against every digit run in the draft, so
     * an opaque value would make the owner's own number read as another party's
     * (contracts/answerer-api.md).
     */
    private Answerer.AnswerContext answerContext(String senderE164) {
        List<String> secrets = new ArrayList<>(ToolNames.all());
        secrets.add(systemPrompt.text());
        return new Answerer.AnswerContext(senderE164, secrets);
    }

    /**
     * Which of the six outcomes this turn had. The three agent outcomes have
     * different owners — a genuine decline is the product working, a redirect for
     * a question the toolkit covers is a prompt defect, and a gate refusal is a
     * vocabulary gap in SPEC-011 — and SC-007 can only count them apart if the
     * record keeps them apart.
     */
    private static TurnDetail.Outcome outcomeOf(GateDecision gate, OutputResult result, TurnScope scope) {
        if (scope.fault().isPresent()) {
            return TurnDetail.Outcome.FAULT;
        }
        if (!result.delivered()) {
            return TurnDetail.Outcome.WITHHELD;
        }
        if (!gate.isAllowed()) {
            return TurnDetail.Outcome.GATE_REFUSED;
        }
        if (result.text().contains(FixedMessages.refusalRedirect())) {
            // The model emitted the out-of-scope marker and the output stage
            // substituted the redirect: the owner sees the product not knowing its
            // own subject, which is counted separately from a true "no basis".
            return TurnDetail.Outcome.AGENT_REDIRECT;
        }
        return scope.toolkitCallCount() == 0
                // No toolkit call behind it, so nothing was grounded: either the
                // agent declined in its own voice, or it asserted something it had
                // no basis for — which the record makes visible either way (SC-002).
                ? TurnDetail.Outcome.AGENT_NO_BASIS
                : TurnDetail.Outcome.ANSWERED;
    }

    private void record(TurnDetail.Builder detail) {
        try {
            turnRecords.record(detail.build());
        } catch (RuntimeException notRecorded) {
            // A reply the owner received but we failed to record is worth a loud
            // log, not a lost answer.
            log.error("could not record the turn", notRecorded);
        }
    }

    /**
     * Brings a decorated reply inside the channel budget without cutting a
     * sentence in half (FR-017).
     *
     * <p>The support line and the advisory are <b>never</b> what gives way:
     * Constitution V makes both obligatory, so the body is trimmed around them.
     * A trim is recorded as a prompt defect — the model was asked to keep the
     * reply to one message and did not.
     */
    static Clamped clamp(String decorated, Sensitivity sensitivityUsed) {
        if (decorated.length() <= REPLY_BUDGET) {
            return new Clamped(decorated, false);
        }
        String prefix = "";
        String body = decorated;
        String supportLine = FixedMessages.selfHarmSupportLine();
        if (body.startsWith(supportLine)) {
            prefix = supportLine + "\n\n";
            body = body.substring(Math.min(prefix.length(), body.length()));
        }
        String suffix = "";
        String note = sensitivityUsed.note();
        if (!note.isBlank() && body.endsWith(note)) {
            suffix = "\n\n" + note;
            body = body.substring(0, body.length() - note.length());
            if (body.endsWith("\n\n")) {
                body = body.substring(0, body.length() - 2);
            }
        }
        int room = REPLY_BUDGET - prefix.length() - suffix.length();
        if (room <= 0) {
            // Both fixed parts alone exceed the budget: nothing left to decide.
            return new Clamped(decorated.substring(0, REPLY_BUDGET), true);
        }
        String kept = body.length() <= room ? body : atSentenceBoundary(body, room);
        return new Clamped(prefix + kept + suffix, true);
    }

    /** The longest prefix of {@code body} within {@code room} that ends a sentence. */
    private static String atSentenceBoundary(String body, int room) {
        String window = body.substring(0, room);
        int cut = -1;
        for (String terminator : List.of(".", "!", "?", "\n")) {
            cut = Math.max(cut, window.lastIndexOf(terminator));
        }
        return cut > 0 ? window.substring(0, cut + 1).trim() : window.trim();
    }

    /** A reply brought inside the budget, and whether anything had to go. */
    record Clamped(String text, boolean trimmed) {}

    /**
     * Keeps what the answerer returned, which {@code GuardedPipeline.handle} does
     * not hand back.
     *
     * <p>The turn record needs the sensitivity the advisory was chosen from, and
     * the clamp needs to know which sentence the output stage appended. Both are
     * on the {@code Answer}, and the pipeline returns only the decorated text — so
     * the value is taken at the one place it passes through.
     */
    private static final class CapturingAnswerer implements Answerer {

        private final Answerer delegate;
        private Answer captured;

        private CapturingAnswerer(Answerer delegate) {
            this.delegate = delegate;
        }

        @Override
        public Answer answer(GateRequest request, AnswerContext context) {
            captured = delegate.answer(request, context);
            return captured;
        }

        /** Empty when the gate refused, because then the answerer was never called. */
        Optional<Sensitivity> sensitivityUsed() {
            return Optional.ofNullable(captured).map(Answer::sensitivityUsed);
        }
    }
}
