package com.celestia.agent.llm;

import com.celestia.core.toolkit.Sensitivity;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything one turn accumulates, and the reason no {@code @Tool} method needs a
 * {@code userId} parameter.
 *
 * <p><b>Why a scope rather than tool arguments.</b> Constitution V and FR-006
 * require every tool call's identity, coordinates and clock to be injected from
 * the authenticated sender, never read from message text. The enforcement point
 * is that there is <b>nowhere to put them</b>: the tool adapters take only what
 * the model may legitimately choose (a house, a matter, a horary number) and read
 * the identity from here. A model that tries to pass someone else's user id has
 * no parameter for it.
 *
 * <p>It also carries what the turn record needs: the tool calls in order, the
 * references minted for their sources, and the most severe sensitivity the answer
 * actually drew on — <b>computed by the application</b>, never read out of the
 * draft and never decided by the model (SPEC-011 FR-014).
 *
 * <p><b>One turn, one thread, no memory.</b> Bound to the calling thread for the
 * duration of {@link OwnerAnswerer#answer}, and removed in a {@code finally}. A
 * turn cannot see a previous one because there is nothing left of it (FR-018).
 */
public final class TurnScope {

    private static final ThreadLocal<TurnScope> CURRENT = new ThreadLocal<>();

    private final TurnContext context;
    private final SourceReferences references = new SourceReferences();
    private final List<ToolCallRecord> toolCalls = new ArrayList<>();
    private final Instant deadline;
    private final int maxToolRounds;

    private Sensitivity sensitivityUsed = Sensitivity.NONE;
    private int rounds;
    private String modelUsed;
    private boolean fallbackSwitched;
    private TurnDetail.FaultKind fault;

    public TurnScope(TurnContext context, Instant startedAt, Duration turnDeadline, int maxToolRounds) {
        this.context = Objects.requireNonNull(context, "context");
        this.deadline = Objects.requireNonNull(startedAt, "startedAt").plus(turnDeadline);
        this.maxToolRounds = maxToolRounds;
    }

    /**
     * The scope for the turn running on this thread.
     *
     * @throws IllegalStateException when a tool is reached outside a turn — which
     *     would mean a second path to the tools, so it fails loudly rather than
     *     inventing an identity
     */
    public static TurnScope current() {
        TurnScope scope = CURRENT.get();
        if (scope == null) {
            throw new IllegalStateException(
                    "no turn in progress: a tool was reached outside OwnerAnswerer.answer, "
                            + "which would mean a second path to the model's tools (Constitution V)");
        }
        return scope;
    }

    /** Whether a turn is in progress on this thread. */
    public static boolean isActive() {
        return CURRENT.get() != null;
    }

    /** Binds this scope to the calling thread; call {@link #close()} in a {@code finally}. */
    public TurnScope open() {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("a turn is already in progress on this thread");
        }
        CURRENT.set(this);
        return this;
    }

    /** Unbinds the scope. Nothing of the turn survives this call (FR-018). */
    public void close() {
        CURRENT.remove();
    }

    public TurnContext context() {
        return context;
    }

    public SourceReferences references() {
        return references;
    }

    public List<ToolCallRecord> toolCalls() {
        return List.copyOf(toolCalls);
    }

    /** The toolkit tool calls — the ones that can ground an interpretation (SC-002). */
    public long toolkitCallCount() {
        return toolCalls.stream().filter(c -> ToolNames.TOOLKIT.contains(c.tool())).count();
    }

    public Sensitivity sensitivityUsed() {
        return sensitivityUsed;
    }

    /**
     * Raises the turn's sensitivity to cover {@code sensitivity}. Called by the
     * toolkit adapters for every result the model is actually shown, so the
     * advisory sentence follows content rather than wording.
     */
    public void sawSensitivity(Sensitivity sensitivity) {
        sensitivityUsed = sensitivityUsed.max(sensitivity);
    }

    /**
     * Opens a record for a tool call the model asked for, with the arguments it
     * supplied — <b>redacted where they are a second person's birth data</b>,
     * because FR-011 forbids persisting that at all and a turn record is
     * persisted. The tool still receives the real arguments; only the record is
     * redacted.
     */
    public void beginCall(String tool, String arguments) {
        toolCalls.add(new ToolCallRecord(rounds + 1, tool, redact(tool, arguments), List.of(), false));
    }

    private static String redact(String tool, String arguments) {
        return "castChartFor".equals(tool)
                ? "{\"redacted\":\"a second person's birth data is used for the reply and not stored (FR-011)\"}"
                : arguments;
    }

    /**
     * Attaches the references a tool produced to its open record, opening one
     * first when the adapter was called directly rather than through the model
     * (which is how the adapters stay unit-testable).
     */
    public void finishCall(String tool, List<String> refs, boolean truncated) {
        for (int i = toolCalls.size() - 1; i >= 0; i--) {
            ToolCallRecord candidate = toolCalls.get(i);
            if (candidate.tool().equals(tool) && candidate.references().isEmpty() && !candidate.truncated()) {
                toolCalls.set(i, candidate.withReferences(refs, truncated));
                return;
            }
        }
        toolCalls.add(new ToolCallRecord(rounds + 1, tool, "{}", refs, truncated));
    }

    /**
     * Counts one completed round of tool calls.
     *
     * @throws TurnBoundExceeded when the round cap is passed — a prompt defect,
     *     reported apart from a timeout because the two mean different things
     *     (contracts/answerer-api.md "Turn budget")
     */
    public void countRound() {
        rounds++;
        if (rounds > maxToolRounds) {
            throw new TurnBoundExceeded(TurnDetail.FaultKind.ROUND_CAP,
                    "the model called tools for " + rounds + " rounds, past the cap of " + maxToolRounds);
        }
    }

    public int rounds() {
        return rounds;
    }

    /**
     * @throws TurnBoundExceeded when the turn has outlived its deadline — a
     *     hosting problem, counted apart from the round cap
     */
    public void checkDeadline(Instant now) {
        if (now.isAfter(deadline)) {
            throw new TurnBoundExceeded(TurnDetail.FaultKind.TIMEOUT,
                    "the turn passed its deadline at " + deadline);
        }
    }

    public Instant deadline() {
        return deadline;
    }

    public Optional<String> modelUsed() {
        return Optional.ofNullable(modelUsed);
    }

    public boolean fallbackSwitched() {
        return fallbackSwitched;
    }

    /** Records which model answered — the provenance a practitioner's verdict depends on. */
    public void usedModel(String model, boolean isFallback) {
        this.modelUsed = model;
        this.fallbackSwitched = this.fallbackSwitched || isFallback;
    }

    /**
     * How the turn failed, when it did. Read by the reply handler, which records
     * it: an operator must be able to tell a fault from a refusal (FR-020).
     */
    public Optional<TurnDetail.FaultKind> fault() {
        return Optional.ofNullable(fault);
    }

    public void faulted(TurnDetail.FaultKind kind) {
        this.fault = kind;
    }
}
