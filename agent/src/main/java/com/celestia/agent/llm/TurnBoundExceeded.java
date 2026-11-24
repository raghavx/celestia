package com.celestia.agent.llm;

/**
 * A turn passed one of its bounds (FR-010). Thrown from inside the tool loop and
 * caught by {@link OwnerAnswerer}, which turns it into a fixed message and a
 * recorded fault — never an exception out of the reply path.
 *
 * <p>The {@link #kind()} is the point: a round cap hit is a prompt defect and a
 * timeout is a hosting one, so collapsing them into one number would make the
 * wrong team look (contracts/answerer-api.md).
 */
public final class TurnBoundExceeded extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final TurnDetail.FaultKind kind;

    public TurnBoundExceeded(TurnDetail.FaultKind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public TurnDetail.FaultKind kind() {
        return kind;
    }
}
