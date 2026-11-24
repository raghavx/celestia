# Quickstart: validating the Owner-Preview Agent (SPEC-012)

How to prove this feature works, in the order the proofs get harder. Steps 1–3
need no model. Step 4 needs a reachable one. Step 5 needs a human.

## Prerequisites

- The toolkit imported (`core/src/main/resources/com/celestia/core/toolkit/`),
  as SPEC-010 left it.
- SPEC-011's guardrail on the classpath — unchanged by this feature.
- MySQL running for the turn record; Docker for the Testcontainers tests
  (ADR-0008).
- **For step 4 only**, three things, and the harness refuses to run without
  them:
  - `celestia.llm.base-url` pointing at a reachable Ollama with the configured
    model pulled. Until the GPU VM exists, a small local model is fine and is the
    point — it exercises ADR-0009's fallback path as the primary path.
  - **the owner's number on `celestia.entitlement.allow-list`** — otherwise
    entitlement turns every question away before the gate and you get ten copies
    of the not-open message;
  - **the owner's birth data seeded** (T030) — otherwise every question returns
    what is missing. Either way ten documents appear and none contains any
    astrology, which is why the harness checks first.

## 1. The guarded path, without a model

```bash
./mvnw -pl agent -am test -Dtest='OwnerAnswererTest,GuardedReplyHandlerTest,SourceReferencesTest'
```

Expected: green. These use a stub `ChatModel`, so they prove the wiring, the
budgets and the reference swap without any inference. In particular
`SourceReferencesTest` asserts the property that matters most here — **no sheet
id, title or row reaches what the model is given** (SC-010), checked on the
serialised request rather than on the reply.

## 1b. Personal data is deleted when it should be

```bash
./mvnw -pl agent -am test -Dtest=TurnRecordRetentionTest
```

Expected: a "forget me" request leaves **zero** turn records for that user; a
record past the retention age is purged; one inside it survives; and with
`celestia.turn-record.retention-days: 0` nothing is purged on age **while
forget-me still deletes everything** (SC-013).

The turn record holds the owner's question, which is a message body and so
personal data under Constitution VII. The first analysis pass found it stored
unconditionally with nothing deleting it — a new table quietly outside the hard
delete that SPEC-008 enumerates. `0` meaning "never purge on age" is a
convenience; it is not a way to switch off deletion, and the test pins that
distinction.

## 2. Entitlement, and that it fails closed

```bash
./mvnw -pl agent -am test -Dtest=EntitlementServiceTest
```

Expected: a listed number is allowed; an unlisted one is denied; and an
unreadable or empty allow-list **denies everyone**. The last of those is the one
worth re-reading — an empty list meaning "allow all" is how a configuration slip
becomes an open service.

Then confirm the order holds, which is the requirement rather than an
implementation detail:

```bash
./mvnw -pl agent -am test -Dtest=GuardedReplyHandlerTest
```

Expected: for an unentitled sender, the gate is never consulted, no chart is
computed and the model is never called. Work done for someone not entitled to it
is work that should not have been done.

## 3. The single path is still structural

```bash
./mvnw -pl agent -am test -Dtest=DeterminismArchitectureTest
```

Expected: green — and this is the first time the single-path rule does real work.
Until now it passed vacuously because no chat model existed. It now names exactly
two classes that may depend on a chat-model type: `GuardedPipeline` and
`OwnerAnswerer`.

**Try breaking it.** Add a field of a Spring AI chat type to any other class and
re-run: the build must fail. If it does not, the rule has been widened to a
package somewhere and the guarantee is gone.

## 3b. The guardrail did not regress

```bash
./mvnw -pl agent -am test -Dtest='GuardrailRegressionTest'
```

Expected: SPEC-011's adversarial corpus replayed through the **real** reply path
— the actual `OwnerAnswerer` bean in place, wrapped in a counting decorator —
with **zero invocations** across the whole file (SC-006). No model is needed:
the assertion is that nothing was called.

This is the check that matters most after wiring. SPEC-011 proved zero
invocations against a *fake* answerer, which was the best proof available before
a model existed, and says nothing about whether a real one introduced a bypass.
This feature adds two things in front of the gate and one behind it; any of them
could have.

The same run covers one more criterion:

| Check | Criterion |
|-------|-----------|
| an unentitled sender gets the fixed message and causes zero invocations | SC-005 |

**SC-012 is not covered here**, despite an earlier draft of this file saying so.
A question that cannot stand alone is tested by `AsksRatherThanGuessesTest`
(T028a), which runs with a stub model:

```bash
./mvnw -pl agent -am test -Dtest='AsksRatherThanGuessesTest,ModelContextLeakTest'
```

`ModelContextLeakTest` is in the same command because it carries the other
property of the stateless decision: the second turn's request contains no
substring of the first message (FR-018).

## 4. A real answer, end to end

```bash
./mvnw -pl agent -am verify -Dgroups=eval
```

Expected: ten questions answered against a real model, each written to
`agent/target/owner-eval/` with the question, the delivered reply, the tool calls
and — resolved from their references — the real toolkit sources behind them.

Mechanically asserted in this run:

| Check | Criterion |
|-------|-----------|
| every reply has ≥ 1 tool call behind it | SC-002 |
| no delivered reply names the toolkit, a sheet or a row | SC-003 |
| nothing was withheld by the leak scan | SC-004 |
| no tool result given to the model contained a sheet id, title or row | SC-010 |
| every reference resolves to a real source | SC-011 |
| answerable questions were answered; no-basis questions were declined | SC-007 |

**A withheld reply is a failure here, not a success.** The leak scan catching
something means the prompt let the model name a source, and the owner lost an
answer. SC-004 is zero for that reason.

## 5. What only you can judge

Read the ten documents in `agent/target/owner-eval/` and mark each answer
acceptable or not as *astrology*. SC-001's bar is nine of ten.

No test can do this. The harness can prove an answer was grounded in a toolkit
rule; it cannot prove the rule was the right one to use, or that the reading
reads like an astrologer rather than a database. That verdict is the gate on
every Stage B topic group too, so the first ten are the precedent.

While reading, two failure modes are worth watching for specifically:

- **A confident answer with a thin basis** — grounded in one marginal rule and
  stated as though it were settled.
- **A decline that should have been an answer.** SC-007 counts these, but only
  you can tell whether the toolkit really had nothing, or whether the model
  failed to look.

## What is deliberately not validated here

- **Latency against SC-008's 30 seconds** — meaningful only on the real GPU VM
  with the model warm (ADR-0010). Measure it there, not on a laptop.
- **Concurrency** — one permit, and the preview has one user. The "busy" path is
  unit-tested, not load-tested.
- **Hindi or Hinglish questions** — the gate refuses them, so the agent never
  sees one. A launch blocker, not a bug here.
- **Follow-up questions that depend on a previous turn** — stateless by decision
  (FR-018). "What about her?" is answered with a question, by design.
