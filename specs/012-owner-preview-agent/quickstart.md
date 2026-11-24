# Quickstart: validating the Owner-Preview Agent (SPEC-012)

How to prove this feature works, in the order the proofs get harder. Steps 1–3
need no model. Step 4 needs a reachable one. Step 5 needs a human.

## Prerequisites

- The toolkit imported (`core/src/main/resources/com/celestia/core/toolkit/`),
  as SPEC-010 left it.
- SPEC-011's guardrail on the classpath — unchanged by this feature.
- MySQL running for the turn record; Docker for the Testcontainers tests
  (ADR-0008).
- **For step 4 only**: `celestia.llm.base-url` pointing at a reachable Ollama
  with the configured model pulled. Until the GPU VM exists, a small local model
  is fine and is the point — it exercises ADR-0009's fallback path as the primary
  path.

## 1. The guarded path, without a model

```bash
./mvnw -pl agent -am test -Dtest='OwnerAnswererTest,GuardedReplyHandlerTest,SourceReferencesTest'
```

Expected: green. These use a stub `ChatModel`, so they prove the wiring, the
budgets and the reference swap without any inference. In particular
`SourceReferencesTest` asserts the property that matters most here — **no sheet
id, title or row reaches what the model is given** (SC-010), checked on the
serialised request rather than on the reply.

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

The same run covers two more criteria:

| Check | Criterion |
|-------|-----------|
| an unentitled sender gets the fixed message and causes zero invocations | SC-005 |
| a question that cannot stand alone is answered with a question, not a guess | SC-012 |

## 4. A real answer, end to end

```bash
./mvnw -pl agent -am verify -Dgroups=eval
```

Expected: ten questions answered against a real model, each written to
`agent/target/owner-eval/` with the question, the delivered reply, the tool calls
and — resolved from their labels — the real toolkit sources behind them.

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
