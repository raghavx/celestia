# Data Model: Owner-Preview Agent (SPEC-012)

Two kinds of thing here: **per-turn values** that live only as long as the turn
that made them, and **one persisted record** written for review. The distinction
matters — the reference→source mapping is deliberately in the first group.

## Per-turn values

### TurnContext

What is assembled before the model is invoked, so the model never has to ask for
the basics (FR-009).

| Field | Type | Rule |
|-------|------|------|
| userId | long | the authenticated sender, from the verified phone number — never from message text (FR-006) |
| question | text | the inbound message, unchanged; the gate has already allowed it |
| chart | `NatalChartBundle` | pre-fetched via the existing `ChartCacheService.natalChart(userId)` |
| runningDasha | dasha period | pre-fetched; the model may not compute it (FR-008) |
| suppliedChart | chart or absent | present only when the owner gave a second person's birth data this turn. **Dies with the turn** (FR-011) |

### SourceReferences

The per-turn mapping that keeps the toolkit's own strings out of the model's
context (FR-013a–d).

| Field | Type | Rule |
|-------|------|------|
| references | ordered list of `{sheetId, sheetTitle, row}` | the real sources, in first-seen order |
| label(i) | text | what the model sees: `s1`, `s2`, … — an **index, not an encoding** |

**Rules**:

1. A source seen twice in one turn gets the **same** label, so the model can
   still recognise that two findings rest on the same rule — SPEC-010
   `agent-tools.md` obligation 3 survives the swap.
2. A label MUST NOT be derived from its source. No hash, no slug, no base64: a
   reversible label puts the string back in the model's context by another route.
3. Labels are scoped to one turn and are not persisted as a reusable map. The
   resolved pairs go into the turn record for review; the mapping itself is not
   a durable index of the toolkit's structure.
4. Nothing that reaches the model — tool result, prompt, or error text — may
   contain a `sheetId`, `sheetTitle` or row number. This is asserted on what the
   model received, not on what it replied (SC-010).

### Answer draft

What `OwnerAnswerer` hands back to `GuardedPipeline`. This is SPEC-011's
`Answerer.Answer`, unchanged: the draft text plus the most severe `Sensitivity`
the answer drew on.

**The sensitivity is reported, not inferred.** It is the maximum over the
`sensitivity` fields of the tool results actually used, computed by the
application — not read out of the draft text, and not decided by the model
(SPEC-011 FR-014).

## Persisted

### TurnRecord

One row per turn, written for the operator and the practitioner (FR-020). The
only new table.

| Field | Type | Rule |
|-------|------|------|
| id | surrogate key | |
| userId | long | who asked |
| receivedAt | timestamp | when the message arrived |
| entitlement | `ALLOWED` · `DENIED` | the first decision made (FR-001) |
| gateOutcome | `GateDecision.Outcome` or absent | absent when entitlement denied, because the gate never ran |
| gateReason | `GateDecision.Reason` or absent | the operator's view, from SPEC-011 |
| outcome | `ANSWERED` · `REFUSED` · `WITHHELD` · `FAULT` | what happened in the end |
| toolCalls | list of `ToolCallRecord` | in order |
| sensitivityUsed | `Sensitivity` | what the advisory was chosen from |
| deliveredLength | int | so an over-budget reply is visible (FR-017, SC-009) |
| faultKind | text or absent | timeout, round cap, model unreachable, malformed tool JSON |

**What it does not hold**: the gate's matched rule never carries message text
(SPEC-011 keeps that true), and this record does not add it back. The question is
stored because a practitioner cannot review an answer without it; nothing else
about the message body is.

### ToolCallRecord

| Field | Type | Rule |
|-------|------|------|
| ordinal | int | which round, so a loop is visible |
| tool | text | the tool name |
| arguments | text | as the model supplied them — **never containing a userId, coordinate or clock**, because those are injected (FR-006) |
| referenceLabels | list of text | the labels this call produced |
| resolvedSources | list of `{sheetId, sheetTitle, row}` | the real sources, resolvable here and nowhere the model can see |
| truncated | bool | SPEC-010 caps results; this says when more existed |

## Entitlement decision

| Field | Type | Rule |
|-------|------|------|
| allowed | bool | |
| basis | `OWNER_ALLOW_LIST` · `DENIED_NOT_LISTED` · `DENIED_UNAVAILABLE` | the third is the fail-closed case (FR-003) |

`DENIED_UNAVAILABLE` exists so a configuration fault is distinguishable from a
stranger. They produce the same fixed message to the sender and different lines
in the log, for the same reason SPEC-011 separated `OFF_TOPIC` from `UNMATCHED`.

## Eval question

| Field | Type | Rule |
|-------|------|------|
| id | text, unique | stable, so a verdict can be compared across runs |
| question | text | as the owner would really type it |
| expectation | `ANSWERABLE` · `NO_BASIS` | which behaviour this case tests (SC-007) |
| note | text | why the case exists |

`NO_BASIS` cases are as important as answerable ones: SC-007 counts both
directions, because a product that declines everything passes an
"invents nothing" test perfectly.

## Order of operations

The sequence is fixed, and the order is the requirement (FR-001, FR-005):

```
1. entitlement     → denied? fixed message. No gate, no chart, no model.
2. non-text        → fixed message. No gate, no model.
3. pre-fetch       → chart + running dasha (FR-009)
4. GuardedPipeline → gate (SPEC-011)
                   → OwnerAnswerer, only if allowed
                       → prompt + tools, references swapped on every result
                   → output stage (SPEC-011): marker, leak scan, support line, advisory
5. turn record     → written whatever happened, including faults
```

Steps 1 and 2 are before the gate on purpose: work done for someone not entitled
to it is work that should not have been done.

## Validation at load (fail fast)

- the allow-list parses as E.164 numbers, or the service denies everyone (FR-003);
- `system-prompt.md` is present and non-empty, and contains each obligation
  `SystemPromptTest` checks for — a prompt missing its leak prohibition is a
  guardrail missing a wall;
- `celestia.llm.base-url` is set, and the model name and fallback name are both
  configured (ADR-0009);
- the turn budget and round cap are positive, so a misconfiguration cannot mean
  "unbounded".
