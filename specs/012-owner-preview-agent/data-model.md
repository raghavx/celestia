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
| sources | ordered list of `{sheetId, sheetTitle, row}` | the real sources, in first-seen order |
| reference(i) | text | what the model sees: `s1`, `s2`, … — an **index, not an encoding** |

**One name for it: "reference".** Earlier drafts also said "label" and "handle"
for the same thing; FR-013a says *reference*, so that is the word used
throughout, and the others are gone.

**Rules**:

1. A source seen twice in one turn gets the **same** reference, so the model can
   still recognise that two findings rest on the same rule — SPEC-010
   `agent-tools.md` obligation 3 survives the swap.
2. A reference MUST NOT be derived from its source. No hash, no slug, no base64: a
   reversible reference puts the string back in the model's context by another route.
3. References are scoped to one turn and are not persisted as a reusable map. The
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

**Where it lives.** SPEC-008 established the layering and
`PersistenceArchitectureTest` guards it: the JPA entity belongs in
`agent.persistence.entity`, its repository in `agent.persistence.repository`,
and a `TurnRecordStore` in `agent.persistence.store` is what the rest of the
application talks to. `agent.llm` holds only the value it hands over — it does
not write persistence directly, for the same reason nothing else in this
codebase does.

| Field | Type | Rule |
|-------|------|------|
| id | surrogate key | |
| userId | long | who asked |
| receivedAt | timestamp | when the message arrived |
| entitlement | `ALLOWED` · `DENIED` | the first decision made (FR-001) |
| question | sized binary-compatible column | the inbound message. **Personal data** (FR-020a); shaped so the deferred column-encryption pass can reach it without a schema change (FR-020c) |
| gateOutcome | `GateDecision.Outcome` or absent | absent when entitlement denied, because the gate never ran |
| gateReason | `GateDecision.Reason` or absent | the operator's view, from SPEC-011 |
| outcome | `ANSWERED` · `AGENT_NO_BASIS` · `AGENT_REDIRECT` · `GATE_REFUSED` · `WITHHELD` · `FAULT` | what happened in the end. The three middle values are the ones a single `REFUSED` hid: **`AGENT_NO_BASIS`** is the agent declining in prose (FR-014), **`AGENT_REDIRECT`** is the model emitting `<<OUT_OF_SCOPE>>` and the output stage substituting the fixed redirect, and **`GATE_REFUSED`** is the gate never letting the question through. They have different owners — a prompt defect, a prompt defect, and a vocabulary gap — and collapsing them makes the wrong team look |
| toolCalls | list of `ToolCallRecord` | in order |
| sensitivityUsed | `Sensitivity` | what the advisory was chosen from |
| deliveredReply | sized binary-compatible column, or absent | what the owner actually received, after the output stage decorated it. Absent on a fault. A practitioner reviewing an answer reads this, not the draft — and it is derived from a message body, so it carries the same obligations as `question` |
| deliveredLength | int | the reply's length kept alongside it, so an over-budget reply is queryable without reading any body (FR-017, SC-009) |
| faultKind | text or absent | timeout, round cap, model unreachable, malformed tool JSON |

**Why the declines are three values and not one.** SC-007 counts two directions
— a question the toolkit covers that was declined, and one it does not cover
that was answered — and neither is derivable from a single `REFUSED`. The
direction itself is a *comparison*, not a field: the eval harness pairs this
`outcome` against the question's `expectation` (`ANSWERABLE` | `NO_BASIS`) and
counts the mismatches in each direction. Live traffic has no expectation to
compare against, which is why the record stores the outcome and the harness
derives the verdict. A product that declined everything would score perfectly on
"invents nothing" and fail here, which is the point.

**What it does not hold**: the gate's matched rule never carries message text
(SPEC-011 keeps that true), and this record does not add it back. Two bodies are
stored and both are load-bearing — the **question**, because a practitioner
cannot review an answer without knowing what was asked, and the **delivered
reply**, because the answer under review is the one the owner received rather
than the model's draft. Nothing else about any message is kept: no normalised
form, no intermediate draft, no earlier turn.

**It is personal data, and it is deleted.** The question is a message body and
the delivered reply is derived from one, so a turn record falls under
Constitution VII — both columns, not just the question. Two rules, and the second must not be
mistaken for a way round the first:

1. **"Forget me" removes it** (FR-020a). SPEC-008 FR-010 enumerates the rows a
   deletion covers; `owner_turn_record` joins that list. A deletion that leaves
   every question a user ever asked is not a deletion.
2. **Age purges it** (FR-020b): records older than
   `celestia.turn-record.retention-days` are purged, default **90**. A value of
   **`0` means never purge on age** — not "never delete", because rule 1 is a
   standing obligation that no setting switches off.

**The purge takes its "now" from an injected `Clock`**, not from the system
clock. Otherwise SC-013's "a record past the retention age is gone" can only be
tested by waiting ninety days. `agent` is a Spring module so Constitution II's
no-ambient-clock rule does not bind it, but the test needs time to be a parameter
all the same.

### ToolCallRecord

| Field | Type | Rule |
|-------|------|------|
| ordinal | int | which round, so a loop is visible |
| tool | text | the tool name |
| arguments | text | as the model supplied them — **never containing a userId, coordinate or clock**, because those are injected (FR-006) |
| references | list of text | the references this call produced |
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
1. entitlement     → denied? fixed message. No permit, no gate, no chart, no model.
2. non-text        → fixed message. No permit taken: a "text only" reply must not
                     consume the one the owner is waiting for.
3. acquire permit  → unavailable? "busy, try again shortly". NO TURN RECORD —
                     no turn began (FR-010b, FR-010c).
   ── a turn starts here ──
4. pre-fetch       → chart + running dasha (FR-009)
5. gate, for the   → GuardedPipeline.gate(request) — the decision the turn
   record             record needs as gateOutcome + gateReason
6. GuardedPipeline → handle(...): gates again, answers only if allowed
                   → OwnerAnswerer, only if allowed
                       → prompt + tools, references swapped on every result
                   → output stage (SPEC-011): marker, leak scan, support line, advisory
7. turn record     → written whatever happened from step 3 on, faults included
8. release permit  → in a finally, or one fault locks the product permanently
```

**Why the gate runs twice.** `handle` returns an `OutputResult` — `delivered`,
the text, and the withheld family — and deliberately does not hand back the
`GateDecision`. But FR-020 requires the record to carry `gateOutcome` and
`gateReason` so an operator can tell a refusal from a fault, and `guardrail` is
not ours to change in this feature. `Gate.decide` is pure, allocates a decision
and touches no I/O, so a second evaluation costs microseconds and buys the
operator's view of every turn. The alternative — inferring "refused" from the
answerer never being invoked — gives the outcome but loses the reason, which is
the half an operator actually reads.

**Why the permit sits at 3.** After entitlement, so a sender who may not be
answered cannot take the only permit and lock the owner out — a
denial-of-service that costs the attacker nothing. Before the gate and the
pre-fetch, so no chart work or gate decision is done for a message that is about
to be turned away. Steps 1–3 are all before the gate on purpose: work done for
someone not entitled to it, or not about to be served, is work that should not
have been done.

**Why a busy rejection has no turn record.** A turn begins when the permit is
acquired (FR-010c), so there is nothing yet to record — no decision, no tool
calls. It must still be **counted**, or the busiest failure mode is the least
visible one.

## Validation at load (fail fast)

- the allow-list parses as E.164 numbers, or the service denies everyone (FR-003);
- `system-prompt.md` is present and non-empty, and contains each obligation
  `SystemPromptTest` checks for — a prompt missing its leak prohibition is a
  guardrail missing a wall;
- `celestia.llm.base-url` is set, and the model name and fallback name are both
  configured (ADR-0009);
- the turn budget and round cap are positive, so a misconfiguration cannot mean
  "unbounded".
