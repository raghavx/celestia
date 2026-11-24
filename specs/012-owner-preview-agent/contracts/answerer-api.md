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
| `recipientRef` | the owner's verified E.164 number, so the leak scan can tell his own identifier from anyone else's |
| `secrets` | the system prompt text and the tool names, so the leak scan can catch them if the model echoes them |

Passing the prompt as a secret is deliberate: it is the string most worth
scanning for, and only `agent` knows it.

## Turn budget

| Bound | Behaviour when exceeded |
|-------|-------------------------|
| **Round cap** — tool-call rounds per turn | End the turn, fixed message, record `faultKind=ROUND_CAP` |
| **Deadline** — wall clock for the whole turn | End the turn, fixed message, record `faultKind=TIMEOUT` |
| **Concurrency** — one permit (ADR-0010) | Second concurrent turn gets "busy, try again shortly"; it is not queued |

Rounds and time are counted separately because the two faults mean different
things: a round cap hit is a prompt defect, a timeout is a hosting one. Collapsing
them into one number makes the wrong team look.

## Fallback

A malformed tool-call response or repeated failure from the primary model
switches that turn to `celestia.llm.fallback-model` (ADR-0009). The switch is
recorded on the turn. **Before the GPU VM exists, a small local model is the
primary**, which exercises this path as the normal path — exactly as ADR-0010
anticipated.

## What this contract does not expose

- No way to reach the chat model other than through `GuardedPipeline`.
- No way to obtain the reference→source mapping from inside a tool result; it
  lives on the turn record.
- No conversation memory. There is no `ChatMemory` bean, and a turn cannot see a
  previous one (FR-018).
