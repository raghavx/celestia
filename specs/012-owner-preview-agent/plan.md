# Implementation Plan: Owner-Preview Agent

**Branch**: `012-owner-preview-agent` | **Date**: 2026-11-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/012-owner-preview-agent/spec.md`

## Summary

Wire the model in, between two halves of a path that already exist.

- **Entitlement first.** A small check in `agent` answers "may this sender be
  answered?" from an allow-list, before the gate and before any chart work. An
  unknown sender costs nothing and reaches nothing (FR-001, research §8).
- **One turn at a time, and the permit's position is the point.** The handler
  takes it after entitlement and before the gate, so a sender who may not be
  answered cannot consume the only permit, and no chart or gate work is done for
  a message about to be told to retry. A turn begins at acquisition, which is
  what makes "every turn is recorded" and "a busy rejection writes nothing"
  consistent (FR-010a–c).
- **The answerer.** `OwnerAnswerer` implements SPEC-011's `Answerer` over Spring
  AI's `OllamaChatModel`, and `GuardedPipeline` is the only thing that calls it.
  The single-path ArchUnit rule splits in two rather than being loosened to a
  package: only two classes may *invoke* a chat model, and only `agent.llm` may
  *depend on* Spring AI at all — the tightest pair that stays true once a model
  exists (research §2).
- **The model sees no toolkit strings.** Tool results pass through the
  application, which swaps each `{sheetId, sheetTitle, row}` for a per-turn
  reference (`s1`, `s2`) before serialisation. The mapping lives in the turn
  record. Constitution X stops resting on the prompt and starts resting on the
  wiring (FR-013a–d, research §3).
- **Stateless turns.** No `ChatMemory`, no carried subject, prompt rebuilt per
  turn — which is also what makes a turn reproducible for the eval harness
  (FR-018, research §4).
- **One prompt, versioned with the code.** `system-prompt.md` on the classpath,
  not a property and not a database row: it is the only thing holding several
  NON-NEGOTIABLE obligations, so it belongs under the same review as code
  (research §5).
- **The eval harness is a document, not an assertion.** Ten questions produce ten
  reviewable files with their resolved sources. What can be checked mechanically
  is; whether the astrology is good is a practitioner's verdict (research §7).

## Technical Context

**Language/Version**: Java 25 (ADR-0007). No new language or build tooling.

**Primary Dependencies**: `spring-ai-starter-model-ollama` (Spring AI 2.0.1,
already in the parent pom's `dependencyManagement`; `agent/pom.xml` anticipates
it in a comment). **`agent` already depends on `core`, `guardrail` and
`channel-whatsapp`** — verified, so the `Answerer` seam is reachable today and
the only new dependency is the Spring AI starter.

**One build setting is load-bearing**: `agent/pom.xml` sets
`maven.compiler.parameters=true`. Spring AI binds `@Tool` arguments **by parameter
name** and `@ToolParam` has no name attribute, so without `-parameters` every tool
argument arrives null and the model's calls silently do nothing — the tool returns
"no such table" for an argument the model supplied correctly. Removing the flag
breaks the tools without breaking the build.

**Storage**: MySQL via the existing stores — `BirthDataStore` for the owner's
seeded birth data, `ChartCacheService.natalChart(userId)` for the pre-fetched
chart. One new table, `owner_turn_record` (Flyway `V8__`), holding the decision,
the tool calls and the resolved sources, reached through a new `TurnRecordStore`
in `agent.persistence.store` with its entity in `agent.persistence.entity` —
the layering SPEC-008 established and `PersistenceArchitectureTest` guards.

**It holds message bodies, so it is deleted.** The record keeps two — the
question and the delivered reply, because a practitioner cannot review an answer
without knowing what was asked and what was received — and both are personal
data under Constitution VII: `owner_turn_record` joins the rows SPEC-008 FR-010's
"forget me" removes (FR-020a), and records are purged past a configurable
retention age, default 90 days, where `0` means never purge on age without ever
disabling the deletion obligation (FR-020b). **Both** columns are shaped so the
deferred column-encryption pass can reach them without a schema change
(FR-020c), the position SPEC-008 took for its own PII columns. The
reference→source mapping is per-turn and dies with the turn.

**Testing**: JUnit 5 + AssertJ + Mockito, as `agent` already uses. **Three tiers,
and knowing which proves what is the point:**

1. **Stub `ChatModel`, default build** — the wiring, the reference swap,
   statelessness, the budgets, the permit, the leak scan. **This is where
   correctness lives**, and it needs no Ollama at all.
2. **Local Ollama + the smallest tool-capable model, by hand** — the round trip only: the client
   connects, a tool call is parsed, the output stage decorates, a reply comes
   back. A model this small will emit malformed tool JSON often, which exercises
   ADR-0009's fallback path as the normal path.
3. **Production VM + Llama 4 Scout, `-Dtest=OwnerEvalHarness`** — the only tier where
   SC-001's practitioner verdict and SC-008's latency mean anything.

Tier 2 cannot fail in a way that condemns the product and tier 3 cannot run on
the dev machine, so a result from the wrong tier is worse than no result.

**Target Platform**: JVM. **Two model environments, by decision rather than by
accident** (ADR-0009, ADR-0010):

| | Production | Development |
|---|---|---|
| Host | warm rented GPU VM (AWS or OCI), `ollama serve` we administer | `ollama serve` on the dev machine — 2019 Intel MacBook Pro, **CPU-only** |
| Model | Llama 4 Scout + fallback | the smallest model that reports the `tools` capability — provisionally `qwen2.5:0.5b`, which T043 verifies and may replace |
| Keep-alive | long — never pay the cold load | short — it is a laptop's RAM |
| Proves | the answers (SC-001, SC-008) | the round trip, and nothing about answer quality |

Both are reached over `celestia.llm.base-url`, and model ids are configuration,
so neither needs a code change.

**Project Type**: Spring Boot application code in the existing `agent` module.

**Performance Goals**: a reply within 30 seconds for 9 of 10 typical questions,
end to end (SC-008). The budget is now meaningful because ADR-0010 settled that
the VM stays warm and `OLLAMA_KEEP_ALIVE` holds the model resident — a cold load
of tens of seconds would have consumed the whole budget on the first question of
a session. Chart and dasha are pre-fetched before the model is invoked, so the
model's own latency is the only variable.

**Constraints**:
- Constitution II: `guardrail` and `core` stay pure. Everything here is in
  `agent`, which is where Spring belongs.
- Constitution IV: the model never calculates. Every number comes from a tool
  result; tool arguments never carry `userId`, coordinates or a clock.
- Constitution V (NON-NEGOTIABLE): one route. `GuardedPipeline` calls the
  answerer; two ArchUnit rules name the only two classes that may *invoke* a
  chat model, and confine Spring AI types to `agent.llm`.
- Constitution VII: personal data now crosses the public internet to the
  inference host (ADR-0010). Transport encryption to that host is required; its
  provider and region are an open sub-decision recorded in ADR-0010, due before
  real traffic rather than before this preview.
- Constitution X (NON-NEGOTIABLE): no reply names the toolkit, a sheet or a row.
  Two controls: the reference swap keeps the strings out of the model's context,
  and SPEC-011's leak scan still runs on the draft.
- ADR-0009: the fallback model is not optional, because this feature is almost
  entirely tool calling and Llama 4's tool-calling under Ollama is immature.

**`ChatModel`, not `ChatClient`, and the reason is the budget.** Spring AI 2.0
runs the tool loop *inside* the chat model and no longer exposes a switch to take
it over; the model calls a `ToolCallingManager` once per round. That makes the
manager the only place a round can be counted, a deadline checked and a tool call
recorded with the arguments the model actually supplied — none of which is
reachable through the fluent client, whose loop is private. So `TurnToolCallingManager`
wraps Spring AI's default manager, and `OwnerAnswerer` drives `ChatModel.call`
directly. One model bean serves both the primary and the fallback, which differ
only in a per-request option, so a switch is an option set rather than a second
client to keep in step.

**`TurnScope` is where identity lives.** No `@Tool` method takes a `userId`, a
coordinate or a clock, so they come from a per-turn scope bound to the answering
thread — opened by the reply handler, closed in a `finally`. The enforcement is
that there is *nowhere to put them*: a model that tries to pass someone else's
identity has no parameter for it (FR-006). The same scope carries the tool calls,
the references and the sensitivity the answer drew on, which is what makes a turn
reviewable afterwards.

**Scale/Scope**: one user. One permit of concurrency (ADR-0010), taken after
entitlement and before the gate; one prompt; two tool adapters exposing the five
toolkit tools plus the engine tools; one new table reached through an
entity/repository/store trio, with a "forget me" delete and a scheduled purge on
a 90-day default; ten eval questions; two ArchUnit rules replacing one; one deleted
class (`EchoReplyHandler`). Thirteen new test classes plus the eval harness: one
needs a real model, one needs MySQL, and the other twelve run on a stub — with
three shared fixtures (`TurnFixtures`, `StubChatModel`, `AnswererHarness`) that
give the stub tier a real chart, a real toolkit and a real tool loop.

## Constitution Check

*GATE: checked before Phase 0 and re-checked after Phase 1. Against **v2.2.0**,
whose amendment of the "self-hosted" constraint is what makes this feature's own
hosting decision lawful rather than merely recorded — see ADR-0010 and the Sync
Impact Report for 2026-11-24.*

| Principle | How this plan complies | Status |
|-----------|------------------------|--------|
| I Spec-driven | spec → clarified (2 questions, both answered) → this plan → tasks. ADR-0009 and ADR-0010 accepted before planning, because the plan cannot name where the model runs without them | ✅ |
| II Deterministic core | Nothing is added to `ephemeris`, `core`, `geo` or `guardrail`. Spring AI lives in `agent` alone, as the Technology Constraints require | ✅ |
| III Correctness gated | The guarded path is covered with a stub model; the eval harness gates the prompt against a real one. A model or prompt change that drops below SC-001's bar is a regression | ✅ |
| IV LLM never calculates | Every number comes from a tool result (FR-008). `userId`, coordinates and clock are injected server-side and never read from tool arguments (FR-006) | ✅ |
| V Guarded single path (NON-NEGOTIABLE) | Entitlement → gate → model → output stage, in that order. `GuardedPipeline` is the only caller of `Answerer`; the single rule becomes two — inference named at `GuardedPipeline` and `OwnerAnswerer`, containment at `agent.llm` — rather than widened to a package (research §2) | ✅ |
| VI Deterministic flows | Not touched — onboarding is SPEC-017. Statelessness here means no flow state exists to keep in the model (FR-018) | n/a |
| VII Data protection | Birth data is seeded, not collected; a supplied second person's data is used for the turn and not persisted (FR-011). The new turn record holds a message body, so it joins the "forget me" delete and carries a 90-day default retention (FR-020a, FR-020b) — the gap the first analysis pass caught. **Remaining exposure**: personal data now reaches a rented inference host, which ADR-0010 records with its open obligations | ⚠️ see note |
| VIII Payments | **Deliberately deviated, and tracked below.** VIII's last clause — every Q&A turn first checks an active subscription with remaining quota — is not satisfied: this feature answers an allow-listed owner with no subscription at all. Payment reconciliation itself is untouched, and `EntitlementService.check` is the seam SPEC-018 fills | ⚠️ tracked |
| IX Adapters at the edge | `ReplyHandler` is unchanged as an interface; the webhook, worker and sender are untouched. Spring AI is a provider type and stays inside `agent.llm` | ✅ |
| X Toolkit is the judgement (NON-NEGOTIABLE) | Interpretation comes only from tool results (FR-014); the answer obligations of `agent-tools.md` are prompt requirements (FR-015); and the toolkit's strings never enter the model's context (FR-013a) while the leak scan still runs | ✅ |

**One piece of scope is specified and not deliverable.** `castChartFor` needs a
place name resolved to coordinates and a zone, and **no geocoder is wired into
this application**: `geo` defines the port (SPEC-007) but nothing implements it,
and handing the model coordinates to supply itself is what FR-006 forbids. The
tool therefore returns "unavailable" so the agent declines rather than inventing
another person's chart. FR-011's plumbing is built and tested — a supplied chart
is used for the turn and never persisted, and `castChartFor`'s arguments are
redacted from the turn record — but **US1 scenario 4 cannot be demonstrated until
a geocoder exists**. Constitution I makes this a defect to resolve rather than
leave: either the spec defers supplied-person charts to SPEC-017, or a task wires
a geocoder. **Open, and owed a decision.**

**Note on VII.** This is the one principle this feature makes materially harder to
satisfy, and the plan does not pretend otherwise. Accepting ADR-0010 option 3
moved inference off the premises, so birth data, names and question text now
travel to a machine in someone else's datacentre. For an owner preview of one
known person that is a defensible exposure; for public traffic it is not.

**Three of those obligations are now constitutional, not aspirational.**
Constitution v2.2.0 defines "self-hosted" as *we run the weights* and, where the
host is rented, requires encrypted transport, a region chosen for the personal
data it will see, and no prompt logging on the host. What remains open in
ADR-0010 is the choice of provider and region — a decision, not a question of
whether it matters. **No violation to track here, but it is the reason this feature must
not quietly become the public launch.**

One tracked deviation (Constitution VIII), recorded under Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/012-owner-preview-agent/
├── plan.md              # this file
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── answerer-api.md  # what agent implements for guardrail, and the turn budget
│   ├── entitlement-api.md # the seam SPEC-018 replaces, and what it must preserve
│   ├── llm-tools.md     # the tool surface the model sees, including the reference swap
│   └── system-prompt.md # the prompt as a contract: what it must say, and why each line
├── checklists/requirements.md
└── spec.md
```

### Source Code (repository root)

```text
agent/src/main/java/com/celestia/agent/
├── AgentMessages.java               # the four fixed texts this feature adds
├── entitlement/
│   ├── EntitlementService.java      # may this sender be answered? allow-list first
│   ├── EntitlementDecision.java     # a basis, not a boolean — SPEC-018 plugs in here
│   └── EntitlementProperties.java   # celestia.entitlement.allow-list
├── llm/
│   ├── package-info.java            # single path, reference swap, prompt-with-the-code
│   ├── LlmConfig.java               # the ChatModel, the toolkit beans, the turn permit
│   ├── LlmProperties.java           # base-url, model, fallback-model, budgets
│   ├── OwnerAnswerer.java           # implements guardrail Answerer — the ONLY chat-model caller
│   ├── TurnToolCallingManager.java  # one round at a time: the cap, the deadline, the records
│   ├── TurnScope.java               # the per-turn scope the tools read their identity from
│   ├── TurnBoundExceeded.java       # a bound was passed; carries which one
│   ├── SystemPrompt.java            # loads system-prompt.md once
│   ├── TurnContext.java             # chart + running dasha, pre-fetched
│   ├── SourceReferences.java        # per-turn {sheetId,sheetTitle,row} -> s1, s2 …
│   ├── SourceRef.java               # a real toolkit source — never serialised to the model
│   ├── ToolCallRecord.java          # one call, for the turn record
│   ├── ToolNames.java               # the closed tool set, engine and toolkit named apart
│   ├── TurnDetail.java              # the value handed to the store: decision, tool calls, sources
│   └── tools/
│       ├── EngineTools.java         # @Tool over core: chart, lords, dasha, horary
│       └── ToolkitTools.java        # @Tool over the five toolkit tools of SPEC-010
├── channel/
│   └── GuardedReplyHandler.java     # entitlement -> GuardedPipeline -> outbound
├── admin/
│   └── SeedOwnerBirthDataCommand.java  # FR-019
├── eval/
│   └── OwnerEvalHarness.java        # ten questions -> reviewable documents
└── persistence/
    ├── entity/OwnerTurnRecordEntity.java   # the JPA entity, where SPEC-008 says entities live
    ├── repository/OwnerTurnRecordRepository.java
    └── store/TurnRecordStore.java          # what llm/ and channel/ talk to; also the purge

agent/src/main/resources/
├── application.yml                  # edited: celestia.llm.*, .entitlement.*, .turn-record.*, .seed-owner.*
├── com/celestia/agent/llm/system-prompt.md
└── db/migration/V8__owner_turn_record.sql

agent/src/test/java/com/celestia/agent/
├── entitlement/EntitlementServiceTest.java      # FR-001..FR-003, fail closed
├── llm/OwnerAnswererTest.java                   # stub model: tool loop, budgets, fallback
├── llm/SourceReferencesTest.java                # FR-013a..d, no toolkit string escapes
├── llm/ModelContextLeakTest.java                # SC-010 + FR-018, asserted on what the model received
├── llm/SystemPromptTest.java                    # every obligation present
├── llm/NoBasisTest.java                         # FR-014, the out-of-scope path
├── llm/AsksRatherThanGuessesTest.java           # FR-018a/b, SC-012 — both directions
├── llm/TurnContextTest.java                     # FR-009, FR-011
├── llm/tools/ToolSetClosedTest.java             # FR-007, the tool set is closed
├── persistence/TurnRecordRetentionIT.java       # FR-020a/b, SC-013 — needs MySQL, hence IT
├── llm/LlmConfigContextTest.java                # exactly one ChatModel, and it is the bounded one
├── channel/GuardedReplyHandlerTest.java         # order: entitlement, gate, model, output
├── channel/GuardrailRegressionTest.java        # SC-006: SPEC-011's corpus through the REAL path, zero invocations
└── architecture/DeterminismArchitectureTest.java # the two single-path rules: inference, containment

agent/src/test/resources/com/celestia/agent/eval/owner-questions.tsv

DELETED: agent/src/main/java/com/celestia/agent/channel/EchoReplyHandler.java
```

**Structure Decision**: three new packages in `agent` — `entitlement`, `llm` and
`eval` — plus one new class in the existing `channel` package and one deleted.
Nothing outside `agent` changes except the ArchUnit rule's allow-list, which is
an `agent` test. `guardrail` and `core` are consumed, not modified: the whole
point of SPEC-011 was that this feature should be able to plug in without
touching the gate.

`agent.llm.tools` is a separate sub-package because the tool adapters are the
one place where Constitution IV is at risk — they are what the model can reach,
and keeping them together makes "what may the model call?" a question you answer
by listing a directory.

## Phase 0 — Research

All unknowns resolved in [research.md](research.md): where the code lives and
what may touch Spring (§1), how the single-path ArchUnit rule survives a real
chat model without being loosened to a package (§2), the per-turn source
reference and why it must not be reversible (§3), stateless turns and what the
prompt must do instead (§4), the prompt as a versioned classpath resource and
the obligations it carries (§5), tool exposure with a round cap as well as a
deadline (§6), what the eval harness can and cannot assert (§7), the entitlement
check placed before the gate and failing closed (§8), replacing rather than
flagging `EchoReplyHandler` (§9), what is deliberately deferred (§10), and how
SC-006 is proved against the real wiring rather than a fake answerer (§11).

## Phase 1 — Design & Contracts

[data-model.md](data-model.md), [contracts/answerer-api.md](contracts/answerer-api.md),
[contracts/llm-tools.md](contracts/llm-tools.md),
[contracts/system-prompt.md](contracts/system-prompt.md),
[quickstart.md](quickstart.md).

### Post-design Constitution re-check

Re-checked against v2.2.0 after the design above. All gates still pass, with
four points worth stating explicitly:

- **V:** the amended rule is **two rules**, because "reaches the model" and
  "imports Spring AI" are different claims and the single rule conflated them.
  **(a) Inference** — only `GuardedPipeline` and `OwnerAnswerer` may *call*
  `ChatModel.call`, `ChatModel.stream` or `ChatClient.prompt`. That is the single
  path, named at two classes. **(b) Containment** — only
  `com.celestia.agent.llm..` and `GuardedPipeline` may *depend on* a chat-model
  package at all, so no controller, worker or handler can hold one. `LlmConfig`
  builds the client and never invokes it, and the tool adapters carry `@Tool`
  from `org.springframework.ai.tool.annotation`: both are lawful under (b) and
  silent under (a). A single "exactly two classes may depend on Spring AI" rule
  would have been red on the day `LlmConfig` was written, and the likely repair
  under build pressure is widening it to a package — which is the second path
  the rule exists to prevent.
- **X:** two independent controls now hold the same duty. The reference swap
  means the model never receives a sheet id, title or row; the leak scan means a
  draft that somehow contains one is still withheld. Neither is removed on
  account of the other.
- **IV:** the tool adapters are the enforcement point. No `@Tool` method accepts
  `userId`, a coordinate or a time as an argument; all three are injected, so a
  model that tries to pass someone else's identity has nowhere to put it.
- **VII:** unchanged from the pre-design check, and the reason this feature is a
  preview rather than a launch. The exposure is recorded in ADR-0010 with its
  obligations open and dated.

## Complexity Tracking

| Deviation | Why it is needed | Why the simpler, compliant path was rejected |
|-----------|------------------|----------------------------------------------|
| **Constitution VIII**: a turn is answered without an active subscription or quota check. An allow-list of one stands in for entitlement | Stage A's exit criterion is the first real answer, and PROJECT_PLAN stages SPEC-012 ahead of SPEC-018 (plan catalog and entitlements) precisely so the agent can be judged before the billing surface exists. An allow-list of one owner is not a payment path being bypassed — it is a payment path that does not exist yet | Building SPEC-018 first would gate the first real answer on plan CRUD, subscription lifecycle and quota arithmetic, none of which this audience of one needs. The deviation is bounded by construction: `EntitlementService.check` is the only entry point, it returns a basis rather than a boolean so "quota exhausted" is expressible without a second call, and SPEC-018 replaces the allow-list source without touching the reply path (contracts/entitlement-api.md). **This is the reason this feature must not quietly become the public launch**, alongside the Constitution VII note above |

The constitution's preamble ("answering is gated by a paid subscription") is the
end state, not a description of the preview. If the preview outlives Stage A,
that is a governance conversation, not a configuration change.
