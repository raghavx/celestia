# Contract: what `agent` implements for the guardrail (SPEC-012)

SPEC-011 defined the seam; this is what fills it. The guardrail is unchanged by
this feature — that was the point of building it first.

## `OwnerAnswerer implements com.celestia.guardrail.Answerer`

```java
Answer answer(GateRequest request, AnswerContext context)
```

**The only class in the system permitted to hold a chat-model type**, alongside
`GuardedPipeline` which calls it. The ArchUnit rule names both explicitly.

| Guarantee | Why |
|-----------|-----|
| Called only by `GuardedPipeline`, only on an `ALLOWED` decision | Constitution V; the rule enforces it |
| Returns `Answer(draft, sensitivityUsed)` where `sensitivityUsed` is the **maximum over the tool results actually used** | The advisory must follow content, not wording (SPEC-011 FR-014) |
| Never returns null and never throws for model trouble | A fault is a fixed message to the owner, not an exception out of the reply path (FR-010) |
| Never writes an advisory sentence or a crisis line | Both belong to the output stage (FR-016) |

## `AnswerContext` — what `agent` supplies

SPEC-011's record, filled as follows:

| Field | Value |
|-------|-------|
| `recipientRef` | the owner's verified E.164 number, **in full and unobscured** |
| `secrets` | the system prompt text and the tool names, so the leak scan can catch them if the model echoes them |

Passing the prompt as a secret is deliberate: it is the string most worth
scanning for, and only `agent` knows it.

**The caller must keep the `Answer` it gets back.** `GuardedPipeline.handle`
returns only an `OutputResult`, and the `sensitivityUsed` the output stage
decorated with is on the `Answer` — so the reply handler wraps the answerer at
this boundary to capture it. Two things depend on that: the turn record's
`sensitivityUsed`, and the reply clamp, which must know **which** advisory
sentence was appended or it trims away the one thing Constitution V makes
obligatory. Reading the value off the turn scope instead is right only while the
answerer happens to report the same number, and that coincidence is not a
contract. Do not "simplify" the wrapper away.

**`recipientRef` must be the real number**, despite `AnswerContext`'s javadoc
calling it "an opaque reference". `OutputStage.otherPartyIdentifier` takes
`digitsOf(recipientRef)` and compares it against every digit run in the draft, so
an identifier the reply contains is only recognised as *the owner's own* if the
reference still holds his digits. Pass a hash or a token and every phone-shaped
number in a reply — his included — reads as another party's identifier, and the
leak scan withholds replies it should deliver. The javadoc is loose; this
contract is the one to follow.

## Turn budget

| Bound | Behaviour when exceeded |
|-------|-------------------------|
| **Round cap** — tool-call rounds per turn | End the turn, fixed message, record `faultKind=ROUND_CAP` |
| **Deadline** — wall clock for the whole turn | End the turn, fixed message, record `faultKind=TIMEOUT` |

**Concurrency is not the answerer's.** The turn permit is taken by the reply
handler, after entitlement and before the gate (FR-010b), so a sender who may
not be answered cannot consume it and no gate work is done for a message about
to be turned away. See data-model.md "Order of operations", step 3.

Rounds and time are counted separately because the two faults mean different
things: a round cap hit is a prompt defect, a timeout is a hosting one. Collapsing
them into one number makes the wrong team look.

**Where the budget is enforced.** Not in the answerer's own loop — there isn't one.
Spring AI 2.0 runs the tool loop inside the chat model and calls a
`ToolCallingManager` once per round, so `TurnToolCallingManager` is where a round
is counted, a deadline checked and a call recorded. This is also why the answerer
holds a `ChatModel` rather than a `ChatClient`: the client's loop is private and
cannot be bounded, and a turn budget that cannot be enforced is a comment.

## Fallback

A malformed tool-call response or repeated failure from the primary model
switches that turn to `celestia.llm.fallback-model` (ADR-0009). The switch is
recorded on the turn as `modelUsed` and `fallbackSwitched` — **not as a fault**: on
a small model it is the ordinary path, and a fault is only recorded when the
fallback fails too. **Before the GPU VM exists, a small local model is the
primary**, which exercises this path as the normal path — exactly as ADR-0010
anticipated.

## What this contract does not expose

- No way to reach the chat model other than through `GuardedPipeline`.
- No way to obtain the reference→source mapping from inside a tool result; it
  lives on the turn record.
- No conversation memory. There is no `ChatMemory` bean, and a turn cannot see a
  previous one (FR-018).
